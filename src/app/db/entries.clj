(ns app.db.entries
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn create-entry!
  [ds {:keys [user-id date activity effect mood-score energy anxiety focus
              sleep-hours note template state-label state-period-id created-at]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :entries
                :values [(cond-> {:user_id user-id
                                  :date date
                                  :activity activity
                                  :effect effect
                                  :mood_score mood-score
                                  :energy energy
                                  :anxiety anxiety
                                  :focus focus
                                  :sleep_hours sleep-hours
                                  :note note
                                  :template template
                                  :state_label state-label
                                  :state_period_id state-period-id}
                           created-at (assoc :created_at created-at))]
                :returning [:*]})
   default-opts))

(defn get-entries
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:= :user_id user-id]
                :order-by [[:date :desc] [:created_at :desc]]})
   default-opts))