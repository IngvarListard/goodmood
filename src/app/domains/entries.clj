(ns app.domains.entries
  (:require [app.db.entries :as db]))

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
                        :state-period-id state_period_id}))

(defn list-entries
  [ds user-id]
  (db/get-entries ds user-id))