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
   [:template {:optional true} [:maybe :string]]])

(defn- today []
  (str (java.time.LocalDate/now)))

(defn- non-nil-str
  "Вернуть пустую строку вместо nil для полей с NOT NULL в схеме БД."
  [v]
  (or v ""))

(defn create-entry
  [ds user-id {:keys [activity effect mood_score energy anxiety focus sleep_hours note template]}]
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
                        :template template}))

(defn list-entries
  [ds user-id]
  (db/get-entries ds user-id))