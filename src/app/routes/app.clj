(ns app.routes.app
  (:require [reitit.ring :as ring]
            [reitit.ring.coercion :as rc]
            [reitit.coercion.malli :as malli]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.params :as params]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.anti-forgery :as anti-forgery]
            [malli.core :as mc]
            [malli.error :as me]
            [malli.transform :as mt]
            [ring.util.response :as response]
            [hiccup.core :as hc]
            [app.middleware :as mw]
            [app.domains.entries :as domains]
            [app.routes.auth :as auth]
            [app.routes.entries :as entries]
            [app.views.entries :as views]
            [app.views.placeholder :as placeholder]
            [app.views.settings :as settings]))

(defn health-check [_]
  (response/response "OK"))

(defn- blank->nil-double
  [v]
  (if (and (string? v) (clojure.string/blank? v))
    nil
    ((mc/decoder :double nil mt/string-transformer) v)))

(def ^:private blank-aware-body-transformer
  (mt/transformer mt/string-transformer {:decoders {:double blank->nil-double}}))

(def ^:private blank-aware-body-provider
  (reify malli/TransformationProvider
    (-transformer [_ options]
      (mt/transformer
       (when (:strip-extra-keys options) (mt/strip-extra-keys-transformer))
       blank-aware-body-transformer
       (when (:default-values options) (mt/default-value-transformer))))))

(def ^:private app-coercion
  (malli/create
   {:transformers
    (assoc (:transformers malli/coercion)
           :body {:default malli/string-transformer-provider
                  :formats {"application/json" blank-aware-body-provider}})}))

(defn- flatten-errors
  [errors]
  (mapcat (fn [[k msgs]]
            (map (fn [m] (str (if (keyword? k) (name k) (str k)) ": " m)) msgs))
          errors))

(defn- coercion-error-middleware
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (= :reitit.coercion/request-coercion (:type data))
            (let [errors (me/humanize (select-keys data [:errors :value]) {:wrap :message})]
              (if (entries/htmx-request? request)
                {:status 400
                 :headers {"Content-Type" "text/html; charset=utf-8"}
                 :body (hc/html (views/error-fragment (flatten-errors errors)))}
                {:status 400
                 :body {:errors errors}}))
            (throw e)))))))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (hc/html body)})

(defn- nav-page-handler
  [title-key]
  (fn [request]
    (html-response 200 (placeholder/page {:title-key title-key} request))))

(defn- settings-page-handler
  [request]
  (html-response 200 (settings/page request)))

(defn- anti-forgery-error-handler
  [_]
  {:status 403
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body "Invalid anti-forgery token"})

(defn- router
  [ds]
  (ring/router
   [["/" {:get {:handler health-check}
          :auth/public true}]
    ["/login" {:get {:handler (partial auth/login-page-handler ds)}
               :post {:handler (partial auth/login-post-handler ds)}
               :auth/public true}]
    ["/logout" {:post {:handler auth/logout-post-handler}
                :auth/public true}]
    ["/locale" {:post {:handler auth/locale-post-handler}
                :auth/public true}]
    ["/dashboard"  {:get {:handler (nav-page-handler :pages/dashboard)}}]
    ["/check-in"   {:get {:handler (nav-page-handler :pages/check-in)}}]
    ["/history"    {:get {:handler (nav-page-handler :pages/history)}}]
    ["/statistics" {:get {:handler (nav-page-handler :pages/statistics)}}]
    ["/insights"   {:get {:handler (nav-page-handler :pages/insights)}}]
    ["/settings"   {:get {:handler settings-page-handler}}]
    ["/entries"
     {:post {:parameters {:body domains/create-entry-schema}
             :handler (partial entries/create-entry ds)}
      :get {:handler (partial entries/get-entries ds)}}]]
   {:data {:coercion app-coercion
           :middleware [rc/coerce-request-middleware
                        rc/coerce-response-middleware]}}))

(defn session-config
  [session-secret]
  {:store (session.cookie/cookie-store {:key (mw/secret-key session-secret)})
   :cookie-name "gm-session"
   :cookie-attrs {:http-only true :same-site :lax :max-age 3600}})

(defn ->app
  [ds session-secret]
  (let [router (router ds)]
    (-> (ring/ring-handler router (ring/create-default-handler))
        coercion-error-middleware
        (mw/require-auth router)
        mw/wrap-identity
        mw/wrap-locale
        (anti-forgery/wrap-anti-forgery
         {:error-handler anti-forgery-error-handler})
        (session/wrap-session (session-config session-secret))
        muuntaja/wrap-format
        params/wrap-params)))