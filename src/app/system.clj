(ns app.system
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [ring.adapter.jetty :as jetty]
            [app.db.migrate :as db.migrate]
            [app.db.seed :as db.seed]
            [app.env :as env]
            [app.domains.push :as push]
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

(defmethod ig/init-key :db/migrate
  [_ {:keys [connection]}]
  (db.migrate/migrate! connection)
  nil)

(defmethod ig/init-key :db/seed
  [_ {:keys [connection]}]
  (db.seed/seed! connection)
  nil)

(defmethod ig/init-key :app.core/secret
  [_ _]
  (or (env/env "GOODMOOD_SESSION_SECRET")
      (throw (ex-info "GOODMOOD_SESSION_SECRET env variable is required" {}))))

(defmethod ig/init-key :app.core/server
  [_ {:keys [port connection session-secret]}]
  (jetty/run-jetty (routes/->app connection session-secret)
                   {:port port :join? false}))

(defmethod ig/halt-key! :app.core/server
  [_ server]
  (.stop server))

;; Фоновый поток-шедулер: тик раз в минуту (design D4). Тик отправляет пуши
;; по слотам и следит за дублированием через общий sentinel last_slot_shown.
;; Без VAPID-ключей тик — no-op (graceful degradation как у AI).
(def tick-interval-ms 60000)

(defmethod ig/init-key :push/scheduler
  [_ {:keys [connection]}]
  (let [stopped (atom false)
        thread (Thread. (fn []
                          (while (not @stopped)
                            (try
                              (push/tick! connection)
                              (catch Exception e
                                (println (str "push scheduler tick failed: "
                                              (.getMessage e)))))
                            (Thread/sleep tick-interval-ms))))]
    (doto thread
      (.setName "push-scheduler")
      (.setDaemon true)
      (.start))
    {:thread thread :stopped stopped}))

(defmethod ig/halt-key! :push/scheduler
  [_ {:keys [thread stopped]}]
  (reset! stopped true)
  (.interrupt thread)
  (.join thread 1000))

(def system-config
  {:db/connection {:path (env/env "GOODMOOD_DB_PATH" "resources/goodmood.db")}
   :db/migrate {:connection (ig/ref :db/connection)}
   :db/seed {:connection (ig/ref :db/connection)
             :migrated (ig/ref :db/migrate)}
   :app.core/secret {}
   :app.core/server {:port (or (some-> (env/env "GOODMOOD_PORT") parse-long) 3000)
                     :connection (ig/ref :db/connection)
                     :session-secret (ig/ref :app.core/secret)}
   ;; Пуш-шедулер: рестарт процесса нужен после добавления компонента
   :push/scheduler {:connection (ig/ref :db/connection)}})

(defn start-system []
  (ig/init system-config))

(defn stop-system [system]
  (ig/halt! system))