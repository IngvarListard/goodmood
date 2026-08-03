(ns app.system
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [app.routes :as routes]))

(defmethod ig/init-key :app.core/server
  [_ {:keys [port]}]
  (jetty/run-jetty routes/app {:port port :join? false}))

(defmethod ig/halt-key! :app.core/server
  [_ server]
  (.stop server))

(def system-config
  {:app.core/server {:port 3000}})

(defn start-system []
  (ig/init system-config))

(defn stop-system [system]
  (ig/halt! system))
