(ns app.domains.entries
  (:require [app.db.entries :as db]
            [app.db.state-periods :as periods]
            [clojure.string :as str]))

(def create-entry-schema
  [:map
   [:mood_score [:int {:min 0 :max 10}]]
   [:energy [:int {:min 0 :max 10}]]
   [:anxiety [:int {:min 0 :max 10}]]
   [:focus {:optional true} [:maybe [:int {:min 0 :max 10}]]]
   [:sleep_hours {:optional true} [:maybe :double]]
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

(defn- non-nil-str
  "Вернуть пустую строку вместо nil для полей с NOT NULL в схеме БД."
  [v]
  (or v ""))

(defn create-entry
  [ds user-id {:keys [activity effect mood_score energy anxiety focus
                      sleep_hours note template state_label state_period_id]}]
  ;; При создании записи, если активен период состояния и не передан явный
  ;; state_period_id — привязать запись к активному периоду (Decision 6.1).
  (let [sp-id (or state_period_id (periods/get-active-period-id ds user-id))]
    (db/create-entry! ds {:user-id user-id
                          :date (today)
                          :activity (non-nil-str activity)
                          :effect (non-nil-str effect)
                          :mood-score mood_score
                          :energy energy
                          :anxiety anxiety
                          :focus focus
                          :sleep-hours sleep_hours
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
   [:sleep_hours {:optional true} [:maybe :double]]
   [:note {:optional true} [:maybe :string]]
   [:activity {:optional true} [:maybe :string]]
   [:state_label {:optional true} [:maybe :string]]])

(def ^:private updatable-fields
  "Редактируемые поля записи (snake, синхронно с update-entry-schema)."
  [:date :mood_score :energy :anxiety :focus :sleep_hours :note :activity
   :state_label])

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
   с nil отбрасываются из SET."
  [ds user-id id params]
  (let [fields (-> params
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