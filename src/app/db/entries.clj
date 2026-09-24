(ns app.db.entries
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn create-entry!
  [ds {:keys [user-id date activity effect mood-score energy anxiety focus
              aggression sleep-hours note template state-label state-period-id
              created-at]}]
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
                                  :aggression aggression
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

(defn get-entries-since
  "Записи пользователя с датой >= since (ISO), по возрастанию даты.
   Ограниченная выборка для оконной агрегации графика (design D7)."
  [ds user-id since]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:and [:= :user_id user-id] [:>= :date since]]
                :order-by [[:date :asc] [:created_at :asc]]})
   default-opts))

(defn get-entry
  "Одна запись пользователя по id (выборка ограничена user-id), или nil."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:and [:= :id id] [:= :user_id user-id]]})
   default-opts))

(defn set-state-label!
  "Установить ручной state_label записи (ограничено user-id).
   Возвращает обновлённую запись или nil (чужая запись / несуществующий id)."
  [ds user-id id label]
  (jdbc/execute-one!
   ds
   (sql/format {:update :entries
                :set {:state_label label}
                :where [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))

(defn update-entry!
  "Обновить переданные поля записи (SET только ключей из fields, snake-case),
   WHERE id AND user_id. Возвращает обновлённую запись или nil (чужая/нет id)."
  [ds user-id id fields]
  (when (seq fields)
    (jdbc/execute-one!
     ds
     (sql/format {:update :entries
                  :set fields
                  :where [:and [:= :id id] [:= :user_id user-id]]
                  :returning [:*]})
     default-opts)))

(defn delete-entry!
  "Удалить запись (hard delete) по id+user_id.
   Возвращает удалённую строку или nil (чужая запись / несуществующий id)."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:delete-from :entries
                :where [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))