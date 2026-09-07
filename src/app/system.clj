(ns app.system
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [ring.adapter.jetty :as jetty]
            [app.db.migrate :as db.migrate]
            [app.domains.users :as users]
            [app.routes.app :as routes]))

(defmethod ig/init-key :db/connection
  [_ {:keys [path]}]
  (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname path})]
    (with-open [conn (jdbc/get-connection ds)]
      ;; WAL + busy_timeout: параллельная запись из разных запросов не рождает
      ;; мгновенный SQLITE_BUSY (live-трафик и параллельные e2e-воркеры)
      (jdbc/execute! conn ["PRAGMA journal_mode=WAL"])
      (jdbc/execute! conn ["PRAGMA busy_timeout=5000"]))
    ds))

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

(defmethod ig/init-key :db/seed
  [_ {:keys [connection]}]
  (users/seed-admin! connection)
  nil)

(defmethod ig/halt-key! :db/seed
  [_ _]
  nil)

(defmethod ig/init-key :app.core/secret
  [_ _]
  (or (System/getenv "GOODMOOD_SESSION_SECRET")
      (throw (ex-info "GOODMOOD_SESSION_SECRET env variable is required" {}))))

(defmethod ig/halt-key! :app.core/secret
  [_ _]
  nil)

(defmethod ig/init-key :app.core/server
  [_ {:keys [port connection session-secret]}]
  (jetty/run-jetty (routes/->app connection session-secret)
                   {:port port :join? false}))

(defmethod ig/halt-key! :app.core/server
  [_ server]
  (.stop server))

(def system-config
  {:db/connection {:path "resources/goodmood.db"}
   :db/migrate {:connection (ig/ref :db/connection)}
   :db/seed {:connection (ig/ref :db/connection)
             :migrated (ig/ref :db/migrate)}
   :app.core/secret {}
   :app.core/server {:port 3000
                     :connection (ig/ref :db/connection)
                     :session-secret (ig/ref :app.core/secret)}})

(defn start-system []
  (ig/init system-config))

(defn stop-system [system]
  (ig/halt! system))