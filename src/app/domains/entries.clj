(ns app.domains.entries
  (:require [app.db.entries :as db]
            [app.db.state-periods :as periods]
            [clojure.string :as str]))

(def max-backfill-days
  "Максимальная глубина бэкфилла даты записи в днях назад от сегодня."
  30)

(defn- valid-backfill-date?
  "ISO-дата в диапазоне [сегодня − max-backfill-days; сегодня] (серверные
   часы). nil — валидно (домен подставит today)."
  [v]
  (or (nil? v)
      (and (string? v)
           (re-matches #"\d{4}-\d{2}-\d{2}" v)
           (try
             (let [d (java.time.LocalDate/parse v)
                   today (java.time.LocalDate/now)
                   earliest (.minusDays today max-backfill-days)]
               (and (not (.isAfter d today))
                    (not (.isBefore d earliest))))
             (catch java.time.format.DateTimeParseException _ false)))))

(def create-entry-schema
  [:map
   [:date {:optional true}
    [:maybe [:and :string [:fn valid-backfill-date?]]]]
   [:mood_score [:int {:min 0 :max 10}]]
   [:energy [:int {:min 0 :max 10}]]
   [:anxiety [:int {:min 0 :max 10}]]
   [:focus {:optional true} [:maybe [:int {:min 0 :max 10}]]]
   [:aggression {:optional true} [:maybe [:int {:min 0 :max 10}]]]
   [:sleep_hours {:optional true} [:maybe :double]]
   [:sleep_minutes {:optional true} [:maybe [:int {:min 0 :max 59}]]]
   [:note {:optional true} [:maybe :string]]
   [:activity {:optional true} [:maybe :string]]
   [:effect {:optional true} [:maybe :string]]
   [:template {:optional true} [:maybe :string]]
   [:state_label {:optional true} [:maybe :string]]
   [:state_period_id {:optional true} [:maybe :int]]])

(defn state-label
  "Определить ярлык состояния по осям розы ветров (rule-based, без AI).
   Правила first-match-wins, только energy и anxiety (focus не влияет).
   Порядок: mixed → anxiety → elevated → low → balanced → neutral.
   Если хотя бы одна из осей не заполнена (nil — старые записи до миграции 004),
   возвращается :state/neutral (недостаточно данных для классификации)."
  [{:keys [energy anxiety]}]
  (if (or (nil? energy) (nil? anxiety))
    :state/neutral
    (cond
      (and (>= energy 7) (>= anxiety 6)) :state/mixed
      (>= anxiety 6) :state/anxiety
      (>= energy 7) :state/elevated
      (<= energy 3) :state/low
      (and (>= energy 4) (<= energy 6)
           (>= anxiety 4) (<= anxiety 5)) :state/balanced
      :else :state/neutral)))

(defn- today []
  (str (java.time.LocalDate/now)))

;; ──────────────────────────────────────────────────────────────
;; Агрегация по дням для линейного графика ленты (design D4/D5/D7):
;; per-day mean непустых значений оси + составная «общее настроение»
;; с инверсией тревоги и агрессии.
;; ──────────────────────────────────────────────────────────────

(def chart-windows
  "Окна периодов графика в днях (design D5): 3 дня / неделя / 30 дней."
  {:3d 3 :week 7 :month 30})

(defn window-dates
  "Даты скользящего окна (строки ISO, по возрастанию, включая сегодня):
   n последних дней. Невалидный период трактуется как неделя."
  ([period] (window-dates period (java.time.LocalDate/now)))
  ([period today]
   (vec (map #(str (.minusDays today %))
             (range (dec (get chart-windows period 7)) -1 -1)))))

(defn- mean-non-nil
  "Среднее непустых значений или nil (непустых нет)."
  [vs]
  (when-let [xs (seq (remove nil? vs))]
    (/ (reduce + xs) (double (count xs)))))

(def ^:private chart-axes
  "Оси графика: ключ датасета → ключ поля записи."
  [[:mood-score :mood-score] [:energy :energy] [:anxiety :anxiety]
   [:focus :focus] [:aggression :aggression]])

(defn- daily-mean
  "Per-day mean непустых значений оси по записям дня или nil."
  [day-entries k]
  (mean-non-nil (map k day-entries)))

(defn- composite-value
  "Составная «общее настроение»: среднее доступных осей
   [mood_score, energy, focus, 10−anxiety, 10−aggression] или nil."
  [day-entries]
  (let [m (daily-mean day-entries :mood-score)
        e (daily-mean day-entries :energy)
        f (daily-mean day-entries :focus)
        a (daily-mean day-entries :anxiety)
        g (daily-mean day-entries :aggression)]
    (mean-non-nil [m e f (when a (- 10 a)) (when g (- 10 g))])))

(defn daily-axis-series
  "Ряды линейного графика по дням для окна периода (design D4/D5/D7).
   Выборка из БД ограничена окном (get-entries-since), а не всей историей.
   Возвращает {:dates [ISO...] :axes {:mood-score [...] :energy [...]
   :anxiety [...] :focus [...] :aggression [...]} :composite [...]};
   день без значений оси → nil (разрыв линии)."
  [ds user-id period]
  (let [dates (window-dates period)
        by-date (group-by :date (db/get-entries-since ds user-id (first dates)))]
    {:dates dates
     :axes (into {}
                 (for [[k entry-k] chart-axes]
                   [k (mapv #(daily-mean (get by-date %) entry-k) dates)]))
     :composite (mapv #(composite-value (get by-date %)) dates)}))

(defn- non-nil-str
  "Вернуть пустую строку вместо nil для полей с NOT NULL в схеме БД."
  [v]
  (or v ""))

(defn- combined-sleep-hours
  "Длительность сна из формы: часы + опциональные минуты → десятичные часы.
   Сна нет (nil), если пусты оба поля или часы пусты и минуты = 0 — дефолт
   select в edit-форме (иначе любое сохранение edit-формы «создавало» бы
   сон 0ч0м у записи без сна). Одиночный sleep_hours без минут проходит
   без изменений (обратная совместимость API)."
  [sleep_hours sleep_minutes]
  (when (or (some? sleep_hours) (and sleep_minutes (pos? sleep_minutes)))
    (+ (or sleep_hours 0) (/ (or sleep_minutes 0) 60.0))))

(defn create-entry
  "Создать запись владельца. date — ISO-дата из формы (бэкфилл) или nil →
   сегодня (серверные часы). Период для привязки (если state_period_id не
   задан явно): период, накрывающий дату записи, иначе активный.
   sleep_hours + опциональные sleep_minutes собираются в десятичные часы."
  [ds user-id {:keys [date activity effect mood_score energy anxiety focus
                      aggression sleep_hours sleep_minutes note template
                      state_label state_period_id]}]
  (let [entry-date (or date (today))
        sp-id (or state_period_id
                  (some-> (periods/get-period-covering-date ds user-id entry-date) :id)
                  (periods/get-active-period-id ds user-id))]
    (db/create-entry! ds {:user-id user-id
                          :date entry-date
                          :activity (non-nil-str activity)
                          :effect (non-nil-str effect)
                          :mood-score mood_score
                          :energy energy
                          :anxiety anxiety
                          :focus focus
                          :aggression aggression
                          :sleep-hours (combined-sleep-hours sleep_hours sleep_minutes)
                          :note note
                          :template template
                          :state-label state_label
                          :state-period-id sp-id})))

(defn list-entries
  [ds user-id]
  (db/get-entries ds user-id))

(def initial-window-days
  "Стартовое окно страниц-списков в днях (design D1): сегодня + 6 предыдущих."
  7)

(def older-chunk-days
  "Размер чанка подгрузки старых дней (design D1)."
  5)

(defn day-chunks
  "Сгруппировать строки (уже date DESC, created_at DESC) в упорядоченный
   вектор [{:date … :entries […]} …], сохраняя порядок (design D5)."
  [rows]
  (->> rows
       (partition-by :date)
       (mapv (fn [day-rows]
               {:date (:date (first day-rows))
                :entries (vec day-rows)}))))

(defn list-entries-initial
  "Записи стартового окна initial-window-days (сегодня − 6 … сегодня)."
  [ds user-id]
  (db/get-entries-since ds user-id
                        (str (.minusDays (java.time.LocalDate/now)
                                         (dec initial-window-days)))))

(defn list-entries-before
  "Следующий чанк старых дней (design D5): get-entries-before → day-chunks →
   первые older-chunk-days дней. Возвращает {:chunks [...] :next-before
   <самая старая дата чанка или nil>}."
  [ds user-id before-date]
  (let [chunks (->> (db/get-entries-before ds user-id before-date)
                    day-chunks
                    (take older-chunk-days)
                    vec)]
    {:chunks chunks
     :next-before (:date (last chunks))}))

(defn latest-entry
  "Последняя запись пользователя или nil (design D9)."
  [ds user-id]
  (db/get-latest-entry ds user-id))

(defn count-entries
  "Общее число записей пользователя (design D8)."
  [ds user-id]
  (db/count-entries ds user-id))

(defn entries-on-date
  "Записи пользователя за конкретную дату (подсчёт остатка дня при удалении
   с ленты после пагинации)."
  [ds user-id date]
  (db/get-entries-on-date ds user-id date))

(def update-entry-schema
  "Схема обновления записи: все поля опциональны — SET строится только из
   переданных ключей. Пустая строка уже превращена в nil коерцией (очистка);
   nil для NOT NULL колонок (date, mood_score) отбрасывается в update-entry."
  [:map
   [:date {:optional true} [:maybe [:re #"^\d{4}-\d{2}-\d{2}$"]]]
   [:mood_score {:optional true} [:maybe [:int {:min 0 :max 10}]]]
   [:energy {:optional true} [:maybe [:int {:min 0 :max 10}]]]
   [:anxiety {:optional true} [:maybe [:int {:min 0 :max 10}]]]
   [:focus {:optional true} [:maybe [:int {:min 0 :max 10}]]]
   [:aggression {:optional true} [:maybe [:int {:min 0 :max 10}]]]
   [:sleep_hours {:optional true} [:maybe :double]]
   [:sleep_minutes {:optional true} [:maybe [:int {:min 0 :max 59}]]]
   [:note {:optional true} [:maybe :string]]
   [:activity {:optional true} [:maybe :string]]
   [:state_label {:optional true} [:maybe :string]]])

(def ^:private updatable-fields
  "Редактируемые поля записи (snake, синхронно с update-entry-schema)."
  [:date :mood_score :energy :anxiety :focus :aggression :sleep_hours :note
   :activity :state_label])

(def ^:private not-null-fields
  "Редактируемые поля с NOT NULL в БД: nil (очистка) не допускается —
   ключ отбрасывается из SET."
  [:date :mood_score])

(defn- blank->nil
  "Пустая строка = очистить поле (nil), иначе значение как есть."
  [v]
  (if (and (string? v) (str/blank? v)) nil v))

(defn update-entry
  "Обновить запись владельца: SET только переданных ключей (частичное
   обновление, чужая запись → nil). Пустая строка → nil (очистка); activity
   NOT NULL — очистка даёт пустую строку (как в create); ключи NOT NULL
   с nil отбрасываются из SET. sleep_minutes, если передан, сливается
   с sleep_hours в десятичные часы."
  [ds user-id id params]
  (let [params (if (contains? params :sleep_minutes)
                 (-> params
                     (assoc :sleep_hours (combined-sleep-hours
                                          (blank->nil (:sleep_hours params))
                                          (blank->nil (:sleep_minutes params))))
                     (dissoc :sleep_minutes))
                 params)
        fields (-> params
                   (select-keys updatable-fields)
                   (update-vals blank->nil))
        fields (if (contains? fields :activity)
                 (update fields :activity #(or % ""))
                 fields)
        fields (apply dissoc fields
                      (for [k not-null-fields
                            :when (nil? (get fields k))]
                        k))]
    (when (seq fields)
      (db/update-entry! ds user-id id fields))))

(defn get-entry
  "Одна запись владельца по id или nil (чужая запись / несуществующий id)."
  [ds user-id id]
  (when id
    (db/get-entry ds user-id id)))

(defn delete-entry
  "Удалить запись владельца (жёстко). Возвращает удалённую запись или nil
   (чужая запись / несуществующий id); периоды и AI-находки не трогаются."
  [ds user-id id]
  (when id
    (db/delete-entry! ds user-id id)))