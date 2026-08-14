(ns app.middleware
  (:require [app.i18n :as i18n]
            [clojure.string :as str]
            [reitit.core :as reitit]
            [ring.util.response :as response]))

(defn secret-key
  "Derive the 16-byte cookie-store key from a session secret string."
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
  "Determine request locale (cookie gm-locale > Accept-Language > default),
   bind it to i18n/*locale* and assoc as :locale on the request."
  [handler]
  (fn [request]
    (let [locale (or (locale-from-cookie request)
                     (locale-from-accept-language request)
                     i18n/default-locale)]
      (binding [i18n/*locale* locale]
        (handler (assoc request :locale locale))))))

(defn wrap-identity
  "Expose the authenticated user from the session as request :identity."
  [handler]
  (fn [request]
    (handler (assoc request :identity (get-in request [:session :identity])))))

(defn- local-redirect
  [location]
  (response/redirect location))

(defn require-auth
  "Protect all routes that are not marked :auth/public in the reitit route
   data. Unauthenticated requests are redirected to /login?next=<uri>.
   Routes with :auth/roles require the identity role to be in that set."
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