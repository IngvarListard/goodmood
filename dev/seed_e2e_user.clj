(ns dev.seed-e2e-user
  "Создать отдельного e2e-юзера с известными учётными данными (идемпотентно).

  Запуск: clj -M -i dev/seed_e2e_user.clj
  Учётные данные берутся из env E2E_USER_EMAIL / E2E_USER_PASSWORD
  (по умолчанию e2e@goodmood.test / e2e-test-password-123)."
  (:require [next.jdbc :as jdbc]
            [app.db.users :as users]
            [buddy.hashers :as hashers]))

(defn- e2e-email []
  (or (System/getenv "E2E_USER_EMAIL") "e2e@goodmood.test"))

(defn- e2e-password []
  (or (System/getenv "E2E_USER_PASSWORD") "e2e-test-password-123"))

(defn -main []
  (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "resources/goodmood.db"})
        email (e2e-email)
        password (e2e-password)]
    (when-not (users/get-user-by-email ds email)
      (users/create-user! ds {:email email
                              :password-hash (hashers/derive password)
                              :display-name "E2E Tester"
                              :role "user"})
      (println (str "Created e2e user: " email)))
    (println "E2E user ready")))

(-main)