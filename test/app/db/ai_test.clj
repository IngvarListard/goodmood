(ns app.db.ai-test
  (:require [app.db.ai :as db]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defonce ^:private tmp-path "/tmp/goodmood-ai-db-test.db")

(def ^:private ds-atom (atom nil))

(def user-id 1)

(defn- migrate! [ds]
  (migratus/migrate {:store :database
                     :migration-dir "migrations"
                     :db {:datasource ds}}))

(defn with-test-db
  [f]
  (let [file (java.io.File. tmp-path)]
    (.delete file)
    (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname tmp-path})
          conn (jdbc/get-connection ds)]
      (.setAutoCommit conn true)
      (try
        (migrate! ds)
        (reset! ds-atom ds)
        (f)
        (finally
          (reset! ds-atom nil)
          (.close conn)
          (.delete file))))))

(use-fixtures :each with-test-db)

(defn- columns-of [table]
  (->> (jdbc/execute! @ds-atom [(str "PRAGMA table_info(" table ")")]
                      {:builder-fn rs/as-unqualified-maps})
       (mapv :name)))

(deftest test-ai-findings-migration-creates-table
  (testing "миграция 009 создаёт ai_findings со всеми колонками"
    (is (= ["id" "user_id" "type" "content" "confidence" "source_refs" "feedback" "hidden" "created_at"]
           (columns-of "ai_findings"))))
  (testing "CHECK-ограничение на type (correlation/label/advice)"
    (is (thrown? java.sql.SQLException
                 (jdbc/execute! @ds-atom
                                ["INSERT INTO ai_findings (user_id, type, content) VALUES (?,?,?)"
                                 1 "bogus" "{}"]))))
  (testing "валидный type сохраняется"
    (jdbc/execute! @ds-atom
                   ["INSERT INTO ai_findings (user_id, type, content, confidence, source_refs) VALUES (?,?,?,?,?)"
                    user-id "correlation" "{\"title\":\"x\"}" "high" "[]"])
    (let [rows (jdbc/execute! @ds-atom ["SELECT * FROM ai_findings"] {:builder-fn rs/as-unqualified-maps})]
      (is (= 1 (count rows)))
      (is (= "{\"title\":\"x\"}" (:content (first rows))) "content хранит JSON строку")))
  (testing "confidence nullable (label/advice не обязаны иметь confidence)"
    (jdbc/execute! @ds-atom
                   ["INSERT INTO ai_findings (user_id, type, content) VALUES (1,'label','{}')"])
    (let [row (first (jdbc/execute! @ds-atom
                                    ["SELECT confidence FROM ai_findings WHERE type='label'"]
                                    {:builder-fn rs/as-unqualified-maps}))]
      (is (nil? (:confidence row)))))
  (testing "hidden по умолчанию 0"
    (let [row (first (jdbc/execute! @ds-atom
                                    ["SELECT hidden FROM ai_findings WHERE type='label'"]
                                    {:builder-fn rs/as-unqualified-maps}))]
      (is (= 0 (:hidden row))))))

(deftest test-ai-findings-migration-rollback-drops-table
  (testing "откат 009 удаляет ai_findings, остальные таблицы целы"
    (jdbc/execute! @ds-atom ["INSERT INTO ai_findings (user_id, type, content) VALUES (?,?,?)"
                             1 "advice" "{}"])
    (jdbc/execute! @ds-atom [(slurp (io/resource "migrations/009-add-ai-findings.down.sql"))])
    (is (= [] (columns-of "ai_findings")) "таблица ai_findings удалена")
    (is (= 15 (count (columns-of "entries"))) "таблица entries не затронута")))

(deftest test-insert-finding-and-get-by-type
  (testing "insert-finding! сохраняет находку и get-findings возвращает её по типу"
    (let [finding (db/insert-finding! @ds-atom
                                      {:user-id user-id
                                       :type "correlation"
                                       :content {:title "сон<6ч и тревога" :description "объяснение"}
                                       :confidence "high"
                                       :source-refs []})]
      (is (some? (:id finding)))
      (let [rows (db/get-findings @ds-atom user-id "correlation")]
        (is (= 1 (count rows)))
        (let [row (first rows)]
          (is (= "correlation" (:type row)))
          (is (= "high" (:confidence row)))
          (is (= {:title "сон<6ч и тревога" :description "объяснение"} (:content row)))
          (is (= 0 (:hidden row))))))))

(deftest test-get-findings-filters-type-and-hidden
  (testing "get-findings фильтрует по типу и скрытым находкам, сортирует по created_at DESC"
    (db/insert-finding! @ds-atom {:user-id user-id :type "correlation"
                                  :content {:title "a"} :confidence "high" :source-refs []})
    (db/insert-finding! @ds-atom {:user-id user-id :type "advice"
                                  :content {:message "b"} :confidence "medium" :source-refs []})
    (is (= 1 (count (db/get-findings @ds-atom user-id "correlation"))))
    (is (= 1 (count (db/get-findings @ds-atom user-id "advice"))))
    (let [id (:id (first (db/get-findings @ds-atom user-id "correlation")))]
      (db/set-feedback! @ds-atom user-id id "already-known")
      (is (= 0 (count (db/get-findings @ds-atom user-id "correlation")))
          "после feedback находка не возвращается (hidden)")
      (is (= 1 (count (db/get-findings @ds-atom user-id "advice")))
          "находки другого типа не затронуты"))))

(deftest test-feedback-hides-without-hard-delete
  (testing "set-feedback! ставит feedback и hidden=1, но строка остаётся в таблице"
    (let [finding (db/insert-finding! @ds-atom {:user-id user-id :type "correlation"
                                                :content {:title "c"} :confidence "low" :source-refs []})
          id (:id finding)
          res (db/set-feedback! @ds-atom user-id id "report")]
      (is (some? res))
      (let [rows (jdbc/execute! @ds-atom ["SELECT id,feedback,hidden FROM ai_findings WHERE id=?" id]
                                {:builder-fn rs/as-unqualified-maps})
            row (first rows)]
        (is (= 1 (count rows)) "строка не удаляется жёстко")
        (is (= "report" (:feedback row)))
        (is (= 1 (:hidden row)))))))

(deftest test-ai-settings-default-and-update
  (testing "get-ai-settings без строки возвращает nil; set-ai-settings! обновляет"
    (is (nil? (db/get-ai-settings @ds-atom user-id))
        "до создания настроек строки нет")
    (db/set-ai-settings! @ds-atom user-id
                         {:master-enabled 1 :correlations-enabled 0
                          :labels-enabled 1 :advice-enabled 1})
    (let [updated (db/get-ai-settings @ds-atom user-id)]
      (is (= 1 (:master-enabled updated)))
      (is (= 0 (:correlations-enabled updated)))
      (is (= 1 (:labels-enabled updated)))
      (is (= 1 (:advice-enabled updated))))))