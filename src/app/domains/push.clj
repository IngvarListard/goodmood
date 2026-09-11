(ns app.domains.push
  "Web push: подписка юзера, отправка пушей по подпискам, чистка мёртвых
   (404/410 от push-сервиса). Graceful degradation: без GOODMOOD_VAPID_*
   отправка не работает (vapid-configured? false), шедулер — no-op
   (паттерн graceful nil как у AI-функций)."
  (:require [app.db.push :as db]
            [app.db.notification-settings :as settings-db]
            [app.domains.notification-settings :as notif]
            [app.env :as env]
            [app.i18n :as i18n]
            [cheshire.core :as json]
            [malli.core :as mc]
            [malli.error :as me])
  (:import [java.security Security]
           [nl.martijndwars.webpush Encoding Notification PushService Subscription Subscription$Keys]
           [org.bouncycastle.jce.provider BouncyCastleProvider]))

;; BC-провайдер нужен web-push (KeyFactory "ECDH", provider "BC");
;; addProvider идемпотентен
(Security/addProvider (BouncyCastleProvider.))

(def subscription-schema
  "Malli-схема подписки Push API (endpoint + ключи шифрования p256dh/auth)."
  [:map {:closed true}
   [:endpoint :string]
   [:keys [:map {:closed true}
           [:p256dh :string]
           [:auth :string]]]])

(defn validate-subscription
  "Валидировать подписку. nil при успехе, map ошибок (humanized) иначе."
  [sub]
  (when-not (mc/validate subscription-schema sub)
    (-> subscription-schema
        (mc/explain sub)
        me/humanize)))

(defn vapid-keys
  "Пара [public private] VAPID-ключей из окружения или nil."
  []
  (when-let [pub (env/env "GOODMOOD_VAPID_PUBLIC_KEY")]
    (when-let [priv (env/env "GOODMOOD_VAPID_PRIVATE_KEY")]
      [pub priv])))

(defn vapid-configured?
  "Пуш-функции доступны (обе VAPID-переменные заданы)?"
  []
  (boolean (vapid-keys)))

(defn send-http
  "Синхронная отправка aes128gcm: обёртка вокруг web-push lib. Отдельная
   функция — точка подмены в тестах (фейковый HttpResponse)."
  [^PushService service ^Notification notification]
  (.send service notification Encoding/AES128GCM))

(defn- send-one!
  "Отправить пуш на одну подписку. 404/410 (сайт снят с телефона) — строка
   подписки удаляется из БД. Любые ошибки (сеть, крипта, мусорный ключ в БД)
   не роняют вызывающего: шедулер живёт дольше одной подписки."
  [ds {:keys [endpoint p256dh auth]} payload-json]
  (let [[pub priv] (vapid-keys)
        response (try
                   (send-http (PushService. pub priv)
                              (Notification.
                               (Subscription. endpoint (Subscription$Keys. p256dh auth))
                               payload-json))
                   (catch Exception e
                     (println (str "push send failed: " (.getMessage e)))
                     ::error))]
    (if (= ::error response)
      ::error
      (let [status (.getStatusCode (.getStatusLine response))]
        ;; Мёртвый endpoint: push-сервис сам его больше не вернёт
        (when (contains? #{404 410} status)
          (db/delete-subscription! ds endpoint))
        status))))

(defn send-push!
  "Отправить пуш юзеру на все его подписки; мёртвые (404/410) удаляются.
   title/body — тексты уведомления. Возвращает число успешных доставок."
  [ds user-id title body]
  (let [payload (json/generate-string {:title title :body body})
        statuses (mapv #(send-one! ds % payload)
                       (db/get-subscriptions ds user-id))]
    (count (filter int? statuses))))

(defn- slot-text
  "Текст пуша для слота из i18n (без чувства вины, design D4)."
  [slot]
  (i18n/t (keyword "push" (str "slot-" slot))))

(defn tick!
  "Один тик шедулера: для каждого юзера с включённым слотом, время которого
   уже прошло, но sentinel last_slot_shown ≠ сегодня — отправить пуш по всем
   подпискам и пометить слот (sentinel общий с in-app баннером). Юзер без
   подписок пропускается — in-app поведение не меняется. Без VAPID — no-op.
   Аргименты today/now — для тестов (серверное локальное время, design D4)."
  ([ds]
   (tick! ds
          (str (java.time.LocalDate/now))
          (str (.truncatedTo (java.time.LocalTime/now)
                             java.time.temporal.ChronoUnit/MINUTES))))
  ([ds today now]
   (when (vapid-configured?)
     (doseq [{:keys [user-id slot]} (settings-db/due-slots ds today now)]
       (when (pos? (send-push! ds user-id (i18n/t :app/name) (slot-text slot)))
         (notif/mark-slot-shown ds user-id slot today))))))