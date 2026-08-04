(ns app.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.coercion :as rc]
            [reitit.coercion.malli :as malli]
            [muuntaja.middleware :as muuntaja]
            [malli.error :as me]
            [ring.util.response :as response]
            [app.domains.entries.handlers :as entries]))

(defn health-check [_]
  (response/response "OK"))

(defn- coercion-error-middleware
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (= :reitit.coercion/request-coercion (:type data))
            {:status 400
             :body {:errors (me/humanize (select-keys data [:errors :value]) {:wrap :message})}}
            (throw e)))))))

(defn- router
  [ds]
  (ring/router
   [["/" {:get {:handler health-check}}]
    ["/entries"
     {:post {:parameters {:body entries/create-entry-schema}
             :handler (partial entries/create-entry ds)}
      :get {:handler (partial entries/get-entries ds)}}]]
   {:data {:coercion malli/coercion
           :middleware [rc/coerce-request-middleware
                        rc/coerce-response-middleware]}}))

(defn ->app
  [ds]
  (ring/ring-handler
   (router ds)
   (ring/create-default-handler)
   {:middleware [muuntaja/wrap-format coercion-error-middleware]}))
