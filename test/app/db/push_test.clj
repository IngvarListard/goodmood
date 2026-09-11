(ns app.db.push-test
  "Тесты CRUD подписок на push (change add-pwa-push)."
  (:require [app.db.push :as db]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:private ds-atom (atom nil))

(def user-id 1)

(def ^:private sub {:endpoint "https://fcm.example.com/fcm/send/ep-1"
                    :p256dh "p256dh-value"
                    :auth "auth-value"})

(use-fixtures :each #(test-helpers/with-test-db :push-db ds-atom %))

(deftest test-subscribe-upserts-by-endpoint
  (testing "повторная подписка на тот же endpoint не плодит дубли"
    (db/subscribe! @ds-atom user-id sub)
    (db/subscribe! @ds-atom user-id sub)
    (is (= 1 (count (db/get-subscriptions @ds-atom user-id))))))

(deftest test-subscribe-updates-keys-on-same-endpoint
  (testing "апсерт обновляет ключи шифрования"
    (db/subscribe! @ds-atom user-id sub)
    (db/subscribe! @ds-atom user-id (assoc sub :p256dh "new-value"))
    (is (= ["new-value"] (mapv :p256dh (db/get-subscriptions @ds-atom user-id))))))

(deftest test-unsubscribe-and-delete
  (testing "unsubscribe! удаляет по endpoint, delete-subscription! тоже"
    (db/subscribe! @ds-atom user-id sub)
    (db/unsubscribe! @ds-atom (:endpoint sub))
    (is (empty? (db/get-subscriptions @ds-atom user-id)))
    (db/subscribe! @ds-atom user-id sub)
    (db/delete-subscription! @ds-atom (:endpoint sub))
    (is (empty? (db/get-subscriptions @ds-atom user-id)))))

(deftest test-get-subscriptions-per-user
  (testing "подписки изолированы по юзеру"
    (db/subscribe! @ds-atom user-id sub)
    (db/subscribe! @ds-atom 2 (assoc sub :endpoint "https://fcm.example.com/ep-2"))
    (is (= 1 (count (db/get-subscriptions @ds-atom user-id))))
    (is (= 1 (count (db/get-subscriptions @ds-atom 2))))
    (is (= "p256dh-value" (:p256dh (first (db/get-subscriptions @ds-atom user-id)))))))