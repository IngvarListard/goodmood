(ns app.db.medications
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn parse-schedule
  "Распарсить JSON-строку расписания в вектор строк времени, пустой вектор при ошибке."
  [s]
  (try
    (vec (json/read-str (or s "[]")))
    (catch Exception _ [])))

(defn serialize-schedule
  "Сериализовать вектор строк времени в JSON-строку для хранения в БД."
  [times]
  (json/write-str (vec times)))

(defn create-medication!
  [ds {:keys [user-id name dose dose-unit schedule sensitive notes]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :medications
                :values [{:user_id user-id
                          :name name
                          :dose dose
                          :dose_unit dose-unit
                          :schedule schedule
                          :sensitive (or sensitive 0)
                          :notes notes}]
                :returning [:*]})
   default-opts))

(defn get-medication
  "Достать медикамент пользователя по id (выборка ограничена user-id)."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from [:medications]
                :where [:and [:= :id id] [:= :user_id user-id]]})
   default-opts))

(defn get-medications-by-user
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:medications]
                :where [:= :user_id user-id]
                :order-by [[:id :asc]]})
   default-opts))

(defn update-medication!
  [ds user-id id {:keys [name dose dose-unit schedule sensitive notes updated-at]}]
  (jdbc/execute-one!
   ds
   (sql/format {:update :medications
                :set {:name name
                      :dose dose
                      :dose_unit dose-unit
                      :schedule schedule
                      :sensitive (or sensitive 0)
                      :notes notes
                      :updated_at updated-at}
                :where [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))

(defn deactivate-medication!
  [ds user-id id updated-at]
  (jdbc/execute-one!
   ds
   (sql/format {:update :medications
                :set {:active 0
                      :updated_at updated-at}
                :where [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))

(defn activate-medication!
  [ds user-id id updated-at]
  (jdbc/execute-one!
   ds
   (sql/format {:update :medications
                :set {:active 1
                      :updated_at updated-at}
                :where [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))

(defn upsert-log!
  "Создать или обновить запись приёма по UNIQUE(medication_id, log_date, scheduled_time)."
  [ds {:keys [user-id medication-id log-date scheduled-time status taken-at actual-dose notes]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :medication_logs
                :values [{:user_id user-id
                          :medication_id medication-id
                          :log_date log-date
                          :scheduled_time scheduled-time
                          :status status
                          :taken_at taken-at
                          :actual_dose actual-dose
                          :notes notes}]
                :on-conflict [:medication_id :log_date :scheduled_time]
                :do-update-set {:status status
                                :taken_at taken-at
                                :actual_dose actual-dose
                                :notes notes}
                :returning [:*]})
   default-opts))

(defn get-log
  "Достать запись приёма медикамента по слоту (medication_id, log_date, scheduled_time)."
  [ds user-id medication-id log-date scheduled-time]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from [:medication_logs]
                :where [:and [:= :user_id user-id]
                        [:= :medication_id medication-id]
                        [:= :log_date log-date]
                        [:= :scheduled_time scheduled-time]]})
   default-opts))

(defn get-logs-by-date
  [ds user-id log-date]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:medication_logs]
                :where [:and [:= :user_id user-id] [:= :log_date log-date]]})
   default-opts))

(defn delete-log!
  [ds user-id medication-id log-date scheduled-time]
  (jdbc/execute-one!
   ds
   (sql/format {:delete-from :medication_logs
                :where [:and [:= :user_id user-id]
                        [:= :medication_id medication-id]
                        [:= :log_date log-date]
                        [:= :scheduled_time scheduled-time]]})
   default-opts))

(defn create-dose-change!
  [ds {:keys [medication-id user-id previous-dose new-dose reason]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :medication_dose_changes
                :values [{:medication_id medication-id
                          :user_id user-id
                          :previous_dose previous-dose
                          :new_dose new-dose
                          :reason reason}]
                :returning [:*]})
   default-opts))

(defn get-dose-changes
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:medication_dose_changes]
                :where [:= :user_id user-id]
                :order-by [[:changed_at :desc]]})
   default-opts))