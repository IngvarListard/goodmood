(ns dev.seed-e2e-user
  "Создать отдельного e2e-юзера с известными учётными данными (идемпотентно).

  Запуск: clj -M -i dev/seed_e2e_user.clj
  Учётные данные берутся из env E2E_USER_EMAIL / E2E_USER_PASSWORD
  (по умолчанию e2e@goodmood.test / e2e-test-password-123)."
  (:require [next.jdbc :as jdbc]
            [clojure.string :as str]
            [app.db.users :as users]
            [buddy.hashers :as hashers]))

(defn- e2e-email []
  (or (System/getenv "E2E_USER_EMAIL") "e2e@goodmood.test"))

(defn- e2e-password []
  (or (System/getenv "E2E_USER_PASSWORD") "e2e-test-password-123"))

(defn- e2e-email []
  (or (System/getenv "E2E_USER_EMAIL") "e2e@goodmood.test"))

(defn- e2e-password []
  (or (System/getenv "E2E_USER_PASSWORD") "e2e-test-password-123"))

(def ^:private empty-user-email "e2e-empty@goodmood.test")

(def ^:private empty-user-password "e2e-test-password-123")

(defn- ensure-user!
  "Создать пользователя, если его нет (идемпотентно)."
  [ds email password display-name]
  (when-not (users/get-user-by-email ds email)
    (users/create-user! ds {:email email
                            :password-hash (hashers/derive password)
                            :display-name display-name
                            :role "user"})
    (println (str "Created e2e user: " email))))

(defn -main []
  (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "resources/goodmood.db"})
        email (e2e-email)
        password (e2e-password)]
    (ensure-user! ds email password "E2E Tester")
    ;; Изолированный юзер для empty-state /feed (гарантированно без записей)
    (ensure-user! ds empty-user-email empty-user-password "E2E Empty")
    ;; Изолированные юзеры для параллельных playwright-воркеров:
    ;; E2E_WORKER_EMAILS="e2e-w0@goodmood.test,e2e-w1@goodmood.test"
    (doseq [w-email (-> (or (System/getenv "E2E_WORKER_EMAILS") "")
                        (str/split #",")
                        (->> (remove str/blank?)))]
      (ensure-user! ds w-email empty-user-password "E2E Tester"))
    (println "E2E user ready")))

(-main)