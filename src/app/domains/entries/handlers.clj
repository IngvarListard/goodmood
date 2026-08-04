(ns app.domains.entries.handlers
  (:require [app.domains.entries.db :as db]))

(def create-entry-schema
  [:map
   [:activity :string]
   [:effect :string]
   [:mood_score [:int {:min 0 :max 10}]]
   [:sleep_hours [:maybe :double]]])

(defn- today []
  (str (java.time.LocalDate/now)))

(defn create-entry
  [ds request]
  (let [{:keys [activity effect mood_score sleep_hours]}
        (get-in request [:parameters :body])]
    {:status 201
     :body (db/create-entry! ds {:date (today)
                                 :activity activity
                                 :effect effect
                                 :mood-score mood_score
                                 :sleep-hours sleep_hours})}))

(defn get-entries
  [ds _request]
  {:status 200
   :body (db/get-entries ds)})
