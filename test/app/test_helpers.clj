(ns app.test-helpers
  "Общая тестовая fixture: изолированная SQLite-БД с миграциями."
  (:require [app.db.migrate :as migrate]
            [next.jdbc :as jdbc]))

(defn with-test-db
  "Fixture: пустая SQLite-БД на tmp-пути (уникальном по id), миграции,
   ds кладётся в ds-atom на время f, после — подчистка. id разделяет
   параллельные тест-файлы."
  [id ds-atom f]
  (let [path (str "/tmp/goodmood-test-" (name id) ".db")
        file (java.io.File. path)]
    (.delete file)
    (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname path})
          conn (jdbc/get-connection ds)]
      (.setAutoCommit conn true)
      (try
        (migrate/migrate! ds)
        (reset! ds-atom ds)
        (f)
        (finally
          (reset! ds-atom nil)
          (.close conn)
          (.delete file))))))
