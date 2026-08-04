(ns app.system
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [ring.adapter.jetty :as jetty]
            [app.db.migrate :as db.migrate]
            [app.routes :as routes]))

(defmethod ig/init-key :db/connection
  [_ {:keys [path]}]
  (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname path})]
    (with-open [_ (jdbc/get-connection ds)]
      ds)))

(defmethod ig/halt-key! :db/connection
  [_ _]
  nil)

(defmethod ig/init-key :db/migrate
  [_ {:keys [connection]}]
  (db.migrate/migrate! connection)
  nil)

(defmethod ig/halt-key! :db/migrate
  [_ _]
  nil)

(defmethod ig/init-key :app.core/server
  [_ {:keys [port connection]}]
  (jetty/run-jetty (routes/->app connection) {:port port :join? false}))

(defmethod ig/halt-key! :app.core/server
  [_ server]
  (.stop server))

(def system-config
  {:db/connection {:path "resources/goodmood.db"}
   :db/migrate {:connection (ig/ref :db/connection)}
   :app.core/server {:port 3000
                     :connection (ig/ref :db/connection)}})

(defn start-system []
  (ig/init system-config))

(defn stop-system [system]
  (ig/halt! system))
