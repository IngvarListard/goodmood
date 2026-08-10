(ns app.db.entries
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn create-entry!
  [ds {:keys [date activity effect mood-score sleep-hours]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :entries
                :values [{:date date
                          :activity activity
                          :effect effect
                          :mood_score mood-score
                          :sleep_hours sleep-hours}]
                :returning [:*]})
   default-opts))

(defn get-entries
  [ds]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :order-by [[:date :desc]]})
   default-opts))