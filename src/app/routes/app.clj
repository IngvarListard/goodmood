(ns app.routes.app
  (:require [reitit.ring :as ring]
            [reitit.ring.coercion :as rc]
            [reitit.coercion.malli :as malli]
            [muuntaja.middleware :as muuntaja]
            [malli.core :as mc]
            [malli.error :as me]
            [malli.transform :as mt]
            [ring.util.response :as response]
            [hiccup.core :as hc]
            [app.domains.entries :as domains]
            [app.routes.entries :as entries]
            [app.views.entries :as views]
            [app.views.placeholder :as placeholder]))

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
  [title]
  (fn [request]
    (html-response 200 (placeholder/page {:title title} request))))

(defn- router
  [ds]
  (ring/router
   [["/" {:get {:handler health-check}}]
    ["/dashboard"  {:get {:handler (nav-page-handler "Дашборд")}}]
    ["/check-in"   {:get {:handler (nav-page-handler "Чек-ин")}}]
    ["/history"    {:get {:handler (nav-page-handler "История")}}]
    ["/statistics" {:get {:handler (nav-page-handler "Статистика")}}]
    ["/insights"   {:get {:handler (nav-page-handler "Инсайты")}}]
    ["/settings"   {:get {:handler (nav-page-handler "Настройки")}}]
    ["/entries"
     {:post {:parameters {:body domains/create-entry-schema}
             :handler (partial entries/create-entry ds)}
      :get {:handler (partial entries/get-entries ds)}}]]
   {:data {:coercion app-coercion
           :middleware [rc/coerce-request-middleware
                        rc/coerce-response-middleware]}}))

(defn ->app
  [ds]
  (ring/ring-handler
   (router ds)
   (ring/create-default-handler)
   {:middleware [muuntaja/wrap-format coercion-error-middleware]}))