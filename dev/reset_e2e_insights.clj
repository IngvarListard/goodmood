(require '[next.jdbc :as jdbc]
         '[next.jdbc.result-set :as rs])
(def ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "resources/goodmood.db"}))
(def opts {:builder-fn rs/as-unqualified-kebab-maps})
;; Юзер берётся из env E2E_USER_EMAIL (для параллельных воркеров), по
;; умолчанию — основной e2e-юзер.
(def email (or (System/getenv "E2E_USER_EMAIL") "e2e@goodmood.test"))
(let [u (first (jdbc/execute! ds ["SELECT id FROM users WHERE email = ?" email] opts))]
  (when u
    (jdbc/execute! ds ["DELETE FROM insights WHERE user_id = ?" (:id u)])))
(println :ok)