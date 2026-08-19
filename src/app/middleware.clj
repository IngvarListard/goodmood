(ns app.middleware
  (:require [app.i18n :as i18n]
            [clojure.string :as str]
            [reitit.core :as reitit]
            [ring.util.response :as response]))

(defn secret-key
  "Вычислить 16-байтовый ключ для cookie-store из строки секрета сессии."
  [session-secret]
  (let [digest (java.security.MessageDigest/getInstance "SHA-256")]
    (->> (.digest digest (.getBytes ^String session-secret "UTF-8"))
         (take 16)
         byte-array)))

(defn- locale-from-cookie
  [request]
  (some-> (get-in request [:cookies "gm-locale" :value])
          i18n/normalize-locale
          i18n/supported-locale))

(defn- locale-from-accept-language
  [request]
  (some-> (get-in request [:headers "accept-language"])
          (str/split #",")
          first
          i18n/normalize-locale
          i18n/supported-locale))

(defn wrap-locale
  "Определить локаль запроса (cookie gm-locale > Accept-Language > умолчание),
   привязать к i18n/*locale* и добавить как :locale в request."
  [handler]
  (fn [request]
    (let [locale (or (locale-from-cookie request)
                     (locale-from-accept-language request)
                     i18n/default-locale)]
      (binding [i18n/*locale* locale]
        (handler (assoc request :locale locale))))))

(defn wrap-identity
  "Достать аутентифицированного пользователя из сессии и добавить как request :identity."
  [handler]
  (fn [request]
    (handler (assoc request :identity (get-in request [:session :identity])))))

(defn read-csrf-token
  "Прочитать CSRF-токен для ring-anti-forgery из form-params, body-params
   (JSON через muuntaja) или заголовков x-csrf-token / x-xsrf-token.
   Используется как :read-token при инициализации wrap-anti-forgery."
  [request]
  (let [form-params (merge (:form-params request)
                           (:multipart-params request))]
    (or (get form-params "__anti-forgery-token")
        (get-in request [:body-params :__anti-forgery-token])
        (get-in request [:headers "x-csrf-token"])
        (get-in request [:headers "x-xsrf-token"]))))

(defn- local-redirect
  [location]
  (response/redirect location))

(defn require-auth
  "Защитить все маршруты, не помеченные :auth/public в данных reitit-роута.
   Неаутентифицированные запросы перенаправляются на /login?next=<uri>.
   Маршруты с :auth/roles требуют, чтобы роль пользователя была в указанном множестве."
  [handler router]
  (fn [request]
    (let [match (reitit/match-by-path router (:uri request))
          route-data (or (:data match) {})
          public? (:auth/public route-data)
          identity (:identity request)
          role (:role identity)
          required-roles (:auth/roles route-data)]
      (cond
        public?
        (handler request)

        (nil? identity)
        (local-redirect (str "/login?next=" (:uri request)))

        (and (seq required-roles)
             (not (contains? (set required-roles) role)))
        (if (reitit/match-by-name router :login)
          (response/status (response/response (str "Forbidden: role " role))
                           403)
          {:status 403
           :headers {"Content-Type" "text/plain; charset=utf-8"}
           :body (str "Forbidden: role " role)})

        :else
        (handler request)))))