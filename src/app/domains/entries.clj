(ns app.domains.entries
  (:require [app.db.entries :as db]
            [app.db.state-periods :as periods]
            [clojure.math :as math]
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
;; Радар периода (change add-period-radar, design D1/D2):
;; среднее непустых значений оси за каждый день окна → взвешенное
;; среднее дней с весами свежести w = 0.5^(возраст/полураспад).
;; ──────────────────────────────────────────────────────────────

(def ^:private period-sizes
  "Размер скользящего окна периода в днях (D2): день / неделя (7) /
   скользящие 30 дней."
  {:day 1 :week 7 :month 30})

(defn period-dates
  "Даты окна периода (строки ISO, включая сегодня): 1/7/30 последних
   дней. Невалидный period трактуется как день."
  ([period] (period-dates period (java.time.LocalDate/now)))
  ([period today]
   (set (map #(str (.minusDays today %))
             (range (get period-sizes period 1))))))

(defn- mean-non-nil
  "Среднее непустых значений или nil (непустых нет)."
  [vs]
  (when-let [xs (seq (remove nil? vs))]
    (/ (reduce + xs) (double (count xs)))))

(defn- axis-value
  "Значение одной оси за период: per-day mean непустых значений →
   взвешенное среднее дней (вес 0.5^(возраст/полураспад), полураспад =
   период/3). Дней со значениями нет → 0. Возраст считается в днях
   между датой дня и today."
  [by-date axis dates today half-life]
  (or (let [days (keep (fn [d]
                         (when-let [m (mean-non-nil (map axis (get by-date d)))]
                           [m (math/pow 0.5 (/ (.between java.time.temporal.ChronoUnit/DAYS
                                                         (java.time.LocalDate/parse d)
                                                         today)
                                               half-life))]))
                       dates)]
        (when-let [xs (seq days)]
          (let [wsum (reduce + (map second xs))]
            (/ (reduce + (map (fn [[v w]] (* v w)) xs)) wsum))))
      0))

(defn period-axes
  "Агрегат осей розы ветров за период (design D1): среднее по дням →
   взвешивание свежести. День с одной и с пятью записями весит одинаково
   (per-day mean непустых значений); старые дни весят меньше свежих
   (полураспад = период/3; у дня окно из одного дня — веса равны).
   Возвращает {:energy :anxiety :focus :mood-score} (двойные, 0 при
   отсутствии значений). Записи с датой вне окна (в т.ч. из будущего)
   исключаются. today — LocalDate для детерминизма в тестах."
  ([entries period] (period-axes entries period (java.time.LocalDate/now)))
  ([entries period today]
   (let [dates (period-dates period today)
         by-date (->> entries
                      (filter #(contains? dates (:date %)))
                      (group-by :date))
         half-life (/ (get period-sizes period 1) 3.0)]
     {:energy (axis-value by-date :energy dates today half-life)
      :anxiety (axis-value by-date :anxiety dates today half-life)
      :focus (axis-value by-date :focus dates today half-life)
      :mood-score (axis-value by-date :mood-score dates today half-life)})))

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