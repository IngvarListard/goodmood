(ns app.core
  (:require [app.system :as system]))

;; Работающая integrant-система, заполненная после старта (см. -main).
;; Из dev-REPL (:7890) — точка доступа к живому состоянию приложения:
;; ds для доменных вызовов — (get app.core/system :db/connection).
(defonce system nil)

(defn -main []
  (let [start-time (System/currentTimeMillis)
        sys (system/start-system)
        elapsed (- (System/currentTimeMillis) start-time)]
    (alter-var-root #'system (constantly sys))
    (println (str "Server started on port 3000 in " elapsed "ms"))
    (when (> elapsed 3000)
      (println "WARNING: Server startup exceeded 3 seconds!"))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(system/stop-system sys)))))
