(ns app.domains.push-test
  "Тесты домена push (change add-pwa-push): выбор слотов шедулером,
   sentinel-семантика, чистка мёртвых подписок (410)."
  (:require [app.db.push :as db]
            [app.db.notification-settings :as settings-db]
            [app.domains.push :as push]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]])
  (:import [java.security KeyPairGenerator]
           [java.util Base64]
           [org.apache.http HttpResponse StatusLine]
           [org.bouncycastle.jce ECNamedCurveTable]))

(def ^:private ds-atom (atom nil))

(def user-id 1)

(def ^:private today "2026-09-11")

;; Notification конструирует PublicKey из p256dh при отправке — фейку нужен
;; валидный EC-ключ (65-байтовая несжатая точка base64url), иначе throw до
;; подменённого send-http. auth — любые 16 байт.
(def ^:private valid-p256dh
  (let [kpg (doto (KeyPairGenerator/getInstance "ECDH" "BC")
              (.initialize (ECNamedCurveTable/getParameterSpec "prime256v1")))]
    (.encodeToString (Base64/getUrlEncoder)
                     (.getEncoded (.getQ (.getPublic (.generateKeyPair kpg)))
                                  false))))

(def ^:private valid-auth "AAAAAAAAAAAAAAAAAAAAAA==")

(def ^:private sub {:endpoint "https://fcm.example.com/fcm/send/ep-1"
                    :p256dh valid-p256dh
                    :auth valid-auth})

(use-fixtures :each #(test-helpers/with-test-db :push-domain ds-atom %))

(defn- fake-response
  "Фейковый HttpResponse с заданным статусом (для подмены send-http)."
  [status]
  (reify HttpResponse
    (getStatusLine [_]
      (reify StatusLine
        (getStatusCode [_] status)))))

(defn- sentinel
  "Значение last_slot_shown для слота юзера."
  [ds slot]
  (:last-slot-shown
   (first (filter #(= slot (:slot %))
                  (settings-db/get-or-default ds user-id)))))

(deftest test-due-slots-picks-exactly-overdue-unshown
  (testing "тик выбирает ровно «просроченные непоказанные» слоты"
    (let [ds @ds-atom]
      (settings-db/get-or-default ds user-id)
      (let [due (settings-db/due-slots ds today "09:00")]
        ;; дефолты: morning 08:00, midday 13:00, evening 19:00
        (is (= [{:user-id user-id :slot "morning"}] due)))
      ;; слот, уже показанный сегодня, не выбирается
      (settings-db/set-slot-shown! ds user-id "morning" today)
      (is (empty? (settings-db/due-slots ds today "09:00")))
      ;; выключенный слот не выбирается
      (settings-db/upsert! ds user-id [{:slot "morning" :time "08:00" :enabled false}])
      (is (empty? (settings-db/due-slots ds today "09:00"))))))

(deftest test-tick-sends-and-marks-sentinel
  (testing "отправка помечает sentinel; второй тик не шлёт"
    (let [ds @ds-atom
          sends (atom [])]
      (settings-db/get-or-default ds user-id)
      ;; фейковая отправка: всегда успех, считает вызовы
      (with-redefs [push/vapid-configured? (constantly true)
                    push/send-push! (fn [_ds uid title body]
                                      (swap! sends conj {:uid uid :title title})
                                      1)]
        ;; подписка есть; morning (08:00) уже прошло, midday/evening — нет
        (db/subscribe! ds user-id sub)
        (push/tick! ds today "09:00")
        (is (= 1 (count @sends)))
        (is (= user-id (:uid (first @sends))))
        (is (= 1 (count (db/get-subscriptions ds user-id))))
        (is (= today (sentinel ds "morning")))
        (is (nil? (sentinel ds "midday")))
        ;; второй тик: слот уже показан — пушей нет
        (reset! sends [])
        (push/tick! ds today "09:00")
        (is (empty? @sends))))))

(deftest test-tick-skips-user-without-subscriptions
  (testing "без подписок — ничего не отправляется и sentinel не ставится"
    (let [ds @ds-atom]
      (settings-db/get-or-default ds user-id)
      (with-redefs [push/vapid-configured? (constantly true)
                    ;; фейк возвращает реальное число доставок: без
                    ;; подписок это 0 — sentinel не ставится
                    push/send-push! (fn [ds uid _title _body]
                                      (count (db/get-subscriptions ds uid)))]
        (push/tick! ds today "09:00"))
      (is (nil? (sentinel ds "morning"))))))

(deftest test-send-one-deletes-dead-subscription
  (testing "410 от push-сервиса удаляет подписку из БД"
    (let [ds @ds-atom
          _ (db/subscribe! ds user-id sub)
          row (first (db/get-subscriptions ds user-id))]
      (with-redefs [push/send-http (constantly (fake-response 410))]
        (is (= 410 (@#'push/send-one! ds row "{}"))))
      (is (empty? (db/get-subscriptions ds user-id))))))

(deftest test-send-one-keeps-alive-subscription
  (testing "успешный статус и временная ошибка подписку не удаляют"
    (let [ds @ds-atom
          _ (db/subscribe! ds user-id sub)
          row (first (db/get-subscriptions ds user-id))]
      (with-redefs [push/send-http (constantly (fake-response 201))]
        (is (= 201 (@#'push/send-one! ds row "{}"))))
      (is (= 1 (count (db/get-subscriptions ds user-id)))))))

(deftest test-tick-noop-without-vapid
  (testing "без VAPID-ключей тик не отправляет и не помечает"
    (let [ds @ds-atom
          _ (db/subscribe! ds user-id sub)]
      (with-redefs [push/vapid-configured? (constantly false)
                    push/send-push! (fn [& _] (is false "не должен вызываться") 1)]
        (push/tick! ds today "09:00"))
      (is (nil? (sentinel ds "morning"))))))