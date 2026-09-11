(ns app.db.push
  "Подписки на web push: CRUD по таблице push_subscriptions (endpoint
   уникален — при повторной подписке юзера с того же браузера строка
   апсертится, design D3)."
  (:require [clojure.set :as set]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn subscribe!
  "Сохранить подписку юзера (upsert по уникальному endpoint). Подписка —
   map {:endpoint :p256dh :auth}. Возвращает строку подписки."
  [ds user-id {:keys [endpoint p256dh auth]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :push_subscriptions
                :values [{:user-id user-id
                          :endpoint endpoint
                          :p256dh p256dh
                          :auth auth}]
                :on-conflict [:endpoint]
                :do-update-set {:user-id user-id
                                :p256dh p256dh
                                :auth auth}})
   default-opts))

(defn unsubscribe!
  "Удалить подписки юзера по endpoint (после unsubscribe() в браузере)."
  [ds endpoint]
  (jdbc/execute-one!
   ds
   (sql/format {:delete-from :push_subscriptions
                :where [:= :endpoint endpoint]})
   default-opts))

(defn get-subscriptions
  "Все подписки юзера (для отправки пуша шедулером). kebab-caser строителя
   результатов превращает колонку p256dh в :p-256dh — переименовываем обратно."
  [ds user-id]
  (mapv #(set/rename-keys % {:p-256dh :p256dh})
         (jdbc/execute!
          ds
          (sql/format {:select [:*]
                       :from [:push_subscriptions]
                       :where [:= :user_id user-id]})
          default-opts)))

(defn delete-subscription!
  "Удалить одну подписку по endpoint (push-сервис вернул 404/410)."
  [ds endpoint]
  (jdbc/execute-one!
   ds
   (sql/format {:delete-from :push_subscriptions
                :where [:= :endpoint endpoint]})
   default-opts))