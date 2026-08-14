(ns app.routes.auth
  (:require [app.domains.users :as users]
            [app.views.auth :as views]
            [hiccup.core :as hc]
            [ring.util.response :as response]))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (hc/html body)})

(defn- safe-next
  "Return next if it is a local path (starts with single /), else nil."
  [next]
  (when (and (string? next)
             (re-matches #"/[^/].*" next))
    next))

(defn- form-param
  [request name]
  (get-in request [:form-params name]))

(defn login-page-handler
  [ds request]
  (if (:identity request)
    (response/redirect "/")
    (let [next (safe-next (get-in request [:query-params "next"]))]
      (html-response 200 (views/login-page request {:next next})))))

(defn login-post-handler
  [ds request]
  (let [email (form-param request "email")
        password (form-param request "password")]
    (if-let [user (users/authenticate ds email password)]
      (let [next (safe-next (form-param request "next"))]
        (-> (response/redirect (or next "/"))
            (assoc :session (assoc (or (:session request) {})
                                  :identity {:id (:id user)
                                             :email (:email user)
                                             :role (:role user)
                                             :display-name (:display-name user)}))))
      (html-response 200 (views/login-page request
                                           {:error :auth/invalid-credentials
                                            :next (safe-next (form-param request "next"))})))))

(defn logout-post-handler
  [request]
  (-> (response/redirect "/login")
      (assoc :session nil)))

(defn locale-post-handler
  "Set the gm-locale cookie and redirect back to :next (or /)."
  [request]
  (let [locale (form-param request "locale")
        next (safe-next (or (form-param request "next") "/"))]
    (-> (response/redirect next)
        (response/set-cookie "gm-locale" locale {:path "/"
                                                 :http-only false
                                                 :same-site :lax}))))