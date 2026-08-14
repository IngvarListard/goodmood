(ns app.domains.entries
  (:require [app.db.entries :as db]))

(def create-entry-schema
  [:map
   [:activity :string]
   [:effect :string]
   [:mood_score [:int {:min 0 :max 10}]]
   [:sleep_hours {:optional true} [:maybe :double]]])

(defn- today []
  (str (java.time.LocalDate/now)))

(defn create-entry
  [ds user-id {:keys [activity effect mood_score sleep_hours]}]
  (db/create-entry! ds {:user-id user-id
                        :date (today)
                        :activity activity
                        :effect effect
                        :mood-score mood_score
                        :sleep-hours sleep_hours}))

(defn list-entries
  [ds user-id]
  (db/get-entries ds user-id))