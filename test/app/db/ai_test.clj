(ns app.db.ai-test
  (:require [app.db.ai :as db]
            [clojure.java.io :as io]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private ds-atom (atom nil))

(def user-id 1)

(use-fixtures :each #(test-helpers/with-test-db :ai-db ds-atom %))

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

;; --- Phase 7: episode warnings ---

(deftest test-episode-warning-migration-creates-table
  (testing "миграция 013 создаёт episode_warnings со всеми колонками"
    (is (= ["id" "user_id" "type" "pattern_description" "confidence" "feedback" "dismissed" "created_at"]
           (columns-of "episode_warnings"))))
  (testing "CHECK-ограничение на type (depressive/hypomanic)"
    (is (thrown? java.sql.SQLException
                 (jdbc/execute! @ds-atom
                                ["INSERT INTO episode_warnings (user_id, type, pattern_description, confidence) VALUES (?,?,?,?)"
                                 1 "bogus" "x" 0.5]))))
  (testing "валидный type + confidence сохраняются, dismissed по умолчанию 0"
    (jdbc/execute! @ds-atom
                   ["INSERT INTO episode_warnings (user_id, type, pattern_description, confidence) VALUES (?,?,?,?)"
                    user-id "hypomanic" "энергия растёт" 0.9])
    (let [rows (jdbc/execute! @ds-atom ["SELECT * FROM episode_warnings"]
                              {:builder-fn rs/as-unqualified-maps})]
      (is (= 1 (count rows)))
      (is (= "hypomanic" (:type (first rows))))
      (is (= 0.9 (:confidence (first rows))))
      (is (= 0 (:dismissed (first rows))))))
  (testing "feedback nullable"
    (let [rows (jdbc/execute! @ds-atom ["SELECT feedback FROM episode_warnings"]
                              {:builder-fn rs/as-unqualified-maps})]
      (is (nil? (:feedback (first rows)))))))

(deftest test-episode-warning-migration-rollback-drops-table
  (testing "откат 013 удаляет episode_warnings и колонку episode_warning_enabled из user_ai_settings"
    (let [content (slurp (io/resource "migrations/013-add-episode-warnings.down.sql"))
          lines (clojure.string/split-lines content)
          code-lines (remove #(clojure.string/starts-with? (clojure.string/trim %) "--") lines)
          joined (clojure.string/join "\n" code-lines)
          stmts (->> (clojure.string/split joined #";")
                     (map clojure.string/trim)
                     (remove clojure.string/blank?))]
      (doseq [stmt stmts]
        (jdbc/execute! @ds-atom [stmt])))
    (is (= [] (columns-of "episode_warnings")) "таблица episode_warnings удалена")
    (is (= ["user_id" "master_enabled" "correlations_enabled" "labels_enabled" "advice_enabled" "allow_novel_advice"]
           (columns-of "user_ai_settings"))
        "колонка episode_warning_enabled удалена из user_ai_settings")))

(deftest test-episode-warning-enabled-default-off
  (testing "episode_warning_enabled по умолчанию OFF (0)"
    (let [row (first (jdbc/execute! @ds-atom
                                    ["SELECT episode_warning_enabled FROM user_ai_settings WHERE user_id=?" user-id]
                                    {:builder-fn rs/as-unqualified-maps}))]
      (is (nil? row) "без настроек opt-in колонки нет (нет строки)"))
    (db/set-ai-settings! @ds-atom user-id
                         {:master-enabled 1 :correlations-enabled 1
                          :labels-enabled 1 :advice-enabled 1})
    (let [row (first (jdbc/execute! @ds-atom
                                    ["SELECT episode_warning_enabled FROM user_ai_settings WHERE user_id=?" user-id]
                                    {:builder-fn rs/as-unqualified-maps}))]
      (is (= 0 (:episode_warning_enabled row)) "opt-in OFF по умолчанию"))))

(deftest test-insert-warning-and-get-active
  (testing "insert-warning! сохраняет warning; get-warnings возвращает активные (dismissed=0)"
    (let [w (db/insert-warning! @ds-atom
                                {:user-id user-id :type "hypomanic"
                                 :pattern-description "энергия растёт, сон падает"
                                 :confidence 0.85})]
      (is (some? (:id w)))
      (let [rows (db/get-warnings @ds-atom user-id)]
        (is (= 1 (count rows)))
        (let [row (first rows)]
          (is (= "hypomanic" (:type row)))
          (is (= "энергия растёт, сон падает" (:pattern-description row)))
          (is (= 0.85 (:confidence row)))
          (is (= 0 (:dismissed row))))))))

(deftest get-warnings-filters-dismissed-and-user
  (testing "get-warnings возвращает только dismissed=0 и только своего пользователя"
    (db/insert-warning! @ds-atom {:user-id user-id :type "depressive" :pattern-description "a" :confidence 0.9})
    (db/insert-warning! @ds-atom {:user-id 999 :type "hypomanic" :pattern-description "b" :confidence 0.9})
    (let [mine (db/insert-warning! @ds-atom {:user-id user-id :type "hypomanic" :pattern-description "c" :confidence 0.8})]
      (db/set-warning-feedback! @ds-atom user-id (:id mine) "false_alarm")
      (let [rows (db/get-warnings @ds-atom user-id)]
        (is (= 1 (count rows)) "dismissed=1 и чужая не возвращаются")
        (is (= user-id (:user-id (first rows))))
        (is (= 0 (:dismissed (first rows))))))))

(deftest set-warning-feedback-marks-dismissed
  (testing "set-warning-feedback! ставит feedback и dismissed=1, но строка остаётся"
    (let [w (db/insert-warning! @ds-atom {:user-id user-id :type "depressive" :pattern-description "x" :confidence 0.8})
          id (:id w)
          res (db/set-warning-feedback! @ds-atom user-id id "false_alarm")]
      (is (some? res))
      (let [row (first (jdbc/execute! @ds-atom
                                      ["SELECT id,feedback,dismissed FROM episode_warnings WHERE id=?" id]
                                      {:builder-fn rs/as-unqualified-maps}))]
        (is (= "false_alarm" (:feedback row)))
        (is (= 1 (:dismissed row))))
      (is (= [] (db/get-warnings @ds-atom user-id)) "после feedback warning скрыт"))))

(deftest episode-warning-toggle-persists
  (testing "set-ai-settings! сохраняет episode_warning_enabled, get-ai-settings возвращает его"
    (db/set-ai-settings! @ds-atom user-id
                        {:master-enabled 1 :correlations-enabled 1
                         :labels-enabled 1 :advice-enabled 1
                         :episode-warning-enabled 1})
    (let [updated (db/get-ai-settings @ds-atom user-id)]
      (is (= 1 (:episode-warning-enabled updated))
          "включение opt-in через set-ai-settings! сохраняется"))
    (db/set-ai-settings! @ds-atom user-id
                        {:master-enabled 1 :correlations-enabled 1
                         :labels-enabled 1 :advice-enabled 1
                         :episode-warning-enabled 0})
    (let [updated (db/get-ai-settings @ds-atom user-id)]
      (is (= 0 (:episode-warning-enabled updated))
          "opt-out через set-ai-settings! сохраняется"))))