(require '[next.jdbc :as jdbc]
         '[next.jdbc.result-set :as rs])
(def ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "resources/goodmood.db"}))
(def opts {:builder-fn rs/as-unqualified-kebab-maps})
(let [u (first (jdbc/execute! ds ["SELECT id FROM users WHERE email = ?" "e2e@goodmood.test"] opts))]
  (when u
    (jdbc/execute! ds ["DELETE FROM medication_logs WHERE user_id = ?" (:id u)])
    (jdbc/execute! ds ["DELETE FROM medication_dose_changes WHERE user_id = ?" (:id u)])
    (jdbc/execute! ds ["DELETE FROM medications WHERE user_id = ?" (:id u)])))
(println :ok)