(ns app.db.notification-settings
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(def slots
  "Три слота уведомлений с временем по умолчанию."
  [["morning" "08:00"]
   ["midday" "13:00"]
   ["evening" "19:00"]])

(defn- seed-defaults!
  "Создать 3 строки по умолчанию (morning/midday/evening, enabled=1).
   Используется при первом обращении пользователя (upsert-on-read)."
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:insert-into :user_notification_settings
                :columns [:user_id :slot :enabled :time]
                :values (mapv (fn [[slot time]]
                                [user-id slot 1 time])
                              slots)})
   default-opts))

(defn- read-settings
  "Прочитать строки настроек пользователя."
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:user_notification_settings]
                :where [:= :user_id user-id]
                :order-by [[:slot :asc]]})
   default-opts))

(defn get-or-default
  "Прочитать настройки слотов пользователя. Если строк нет (первое
   обращение) — создать дефолтные (morning 08:00 / midday 13:00 /
   evening 19:00, все включены) и вернуть их."
  [ds user-id]
  (let [rows (read-settings ds user-id)]
    (if (seq rows)
      rows
      (do (seed-defaults! ds user-id)
          (read-settings ds user-id)))))

(defn upsert!
  "Обновить слоты пользователя (enabled, time). Параметры — вектор maps
   вида {:slot :time :enabled}. Если строки ещё нет — создаётся."
  [ds user-id slot-updates]
  (doseq [{:keys [slot time enabled]} slot-updates]
    (jdbc/execute-one!
     ds
     (sql/format {:insert-into :user_notification_settings
                  :values [{:user-id user-id
                            :slot slot
                            :enabled (if enabled 1 0)
                            :time time}]
                  :on-conflict [:user-id :slot]
                  :do-update-set {:enabled (if enabled 1 0)
                                  :time time}})
     default-opts))
  (read-settings ds user-id))

(defn set-summary-shown!
  "Пометить вечернюю сводку показанной сегодня (last_summary_date = date)
   для слота evening. Если строки нет — создаётся дефолтная."
  [ds user-id date]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :user_notification_settings
                :values [{:user_id user-id
                          :slot "evening"
                          :enabled 1
                          :time "19:00"
                          :last_summary_date date}]
                :on-conflict [:user-id :slot]
                :do-update-set {:last_summary_date date}})
   default-opts))

(defn set-slot-shown!
  "Пометить слот показанным сегодня (last_slot_shown = date).
   Используется баннером «пока тебя не было»."
  [ds user-id slot date]
  (jdbc/execute-one!
   ds
   (sql/format {:update :user_notification_settings
                :set {:last_slot_shown date}
                :where [:and [:= :user_id user-id] [:= :slot slot]]})
   default-opts))