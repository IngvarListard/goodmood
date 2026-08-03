(ns app.core
  (:require [app.system :as system]))

(defn -main []
  (let [start-time (System/currentTimeMillis)
        sys (system/start-system)
        elapsed (- (System/currentTimeMillis) start-time)]
    (println (str "Server started on port 3000 in " elapsed "ms"))
    (when (> elapsed 3000)
      (println "WARNING: Server startup exceeded 3 seconds!"))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(system/stop-system sys)))))
