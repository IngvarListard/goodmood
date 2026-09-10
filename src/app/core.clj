(ns app.core
  ;; :gen-class обязателен для uberjar: без него AOT не пишет app/core.class
  ;; и Main-Class: app.core в манифесте не резолвится (ClassNotFoundException).
  (:require [app.system :as system])
  (:gen-class))

;; Работающая integrant-система, заполненная после старта (см. -main).
;; Из dev-REPL (:7890) — точка доступа к живому состоянию приложения:
;; ds для доменных вызовов — (get app.core/system :db/connection).
(defonce system nil)

(defn -main []
  (let [start-time (System/currentTimeMillis)
        sys (system/start-system)
        elapsed (- (System/currentTimeMillis) start-time)]
    (alter-var-root #'system (constantly sys))
    (println (str "Server started in " elapsed "ms"))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(system/stop-system sys)))))
