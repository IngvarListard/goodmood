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

(defn wrap-request-log
  "Логгер HTTP-запросов (access log в stdout). Печатает метод, путь, статус,
   длительность в мс и id пользователя (если аутентифицирован). Исключения
   оборачивает в 500 и логирует стектрейс. Комментарии на русском, сам лог —
   на английском, в машиночитаемом формате."
  [handler]
  (fn [request]
    (let [start (System/nanoTime)
          method (str/upper-case (name (or (:request-method request) :get)))
          uri (:uri request)
          query (:query-string request)
          path (if (seq query) (str uri "?" query) uri)]
      (try
        (let [response (handler request)
              status (or (:status response) 200)
              elapsed-ms (-> (- (System/nanoTime) start) (/ 1e6) double (Math/round))
              uid (get-in request [:identity :id])]
          (println (format "%s %s -> %d (%dms) user=%s"
                           (.format (java.time.LocalDateTime/now)
                                    (java.time.format.DateTimeFormatter/ofPattern
                                     "yyyy-MM-dd HH:mm:ss.SSS"))
                           path status elapsed-ms (or (some-> uid str) "-")))
          response)
        (catch Throwable e
          (println (format "%s %s -> [ERROR] %s"
                          (java.time.Instant/now)
                          path
                          (.getMessage e)))
          (.printStackTrace e)
          {:status 500
           :headers {"Content-Type" "text/plain; charset=utf-8"}
           :body "Internal Server Error"})))))

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

(defn- theme-from-cookie
  [request]
  (let [value (get-in request [:cookies "gm-theme" :value])]
    (when (contains? #{"light" "dark"} value)
      (keyword value))))

(defn wrap-theme
  "Определить тему запроса из cookie gm-theme (:light | :dark, иначе :system),
   добавить как :theme в request. По аналогии с wrap-locale."
  [handler]
  (fn [request]
    (handler (assoc request :theme (or (theme-from-cookie request) :system)))))

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
        (response/redirect (str "/login?next=" (:uri request)))

        (and (seq required-roles)
             (not (contains? (set required-roles) role)))
        (response/status (response/response (str "Forbidden: role " role))
                         403)

        :else
        (handler request)))))