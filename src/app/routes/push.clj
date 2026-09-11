(ns app.routes.push
  "Роуты подписки на push (change add-pwa-push): POST /push/subscribe и
   POST /push/unsubscribe. Тело — JSON от push.js; ответ — HTML-фрагмент
   секции настроек (свап выполняет push.js)."
  (:require [app.db.push :as db]
            [app.domains.push :as push-domains]
            [app.routes.html :refer [html-response]]
            [app.views.settings :as settings]))

(defn- user-id
  [request]
  (get-in request [:identity :id]))

(defn- subscribed?
  "Есть ли у юзера хоть одна подписка на push."
  [ds uid]
  (boolean (seq (db/get-subscriptions ds uid))))

(defn- section-fragment
  "Свежая секция «Push-уведомления»: сервер рендерит состояние, push.js
   подставляет его вместо текущей секции."
  [request subscribed?]
  (settings/push-section (push-domains/vapid-configured?) subscribed?))

(defn subscribe
  "POST /push/subscribe — сохранить подписку юзера.
   Тело JSON {endpoint, keys {p256dh, auth}}. Без VAPID-ключей на сервере
   и с невалидной подпиской — 400 (push.js покажет свой статус)."
  [ds request]
  (let [sub (:body-params request)
        uid (user-id request)]
    (cond
      (not (push-domains/vapid-configured?))
      {:status 400 :body "push not configured"}

      (not (map? sub))
      {:status 400 :body "invalid body"}

      :else
      (if-let [errors (push-domains/validate-subscription sub)]
        {:status 400 :body (str errors)}
        (do (db/subscribe! ds uid {:endpoint (:endpoint sub)
                                   :p256dh (get-in sub [:keys :p256dh])
                                   :auth (get-in sub [:keys :auth])})
            (html-response 200 (section-fragment request true)))))))

(defn unsubscribe
  "POST /push/unsubscribe — удалить подписку по endpoint (браузер уже
   сделал unsubscribe()). Ответ — свежая секция настроек."
  [ds request]
  (let [body (:body-params request)]
    (when (and (map? body) (string? (:endpoint body)))
      (db/unsubscribe! ds (:endpoint body)))
    (html-response 200 (section-fragment request (subscribed? ds (user-id request))))))