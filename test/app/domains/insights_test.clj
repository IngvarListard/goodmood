(ns app.domains.insights-test
  (:require [app.db.insights :as db]
            [app.domains.insights :as domains]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]))

(defonce ^:private tmp-path "/tmp/goodmood-insights-domain-test.db")

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

(deftest test-create-insight-validation
  (testing "валидный инсайт создаётся"
    (let [insight (domains/create-insight
                   @ds-atom user-id
                   {:context "контекст"
                    :category "coping"
                    :advice_to_self ["совет 1" "совет 2"]
                    :identity "я спокоен"
                    :state_label "anxiety"
                    :entry_id nil})]
      (is (some? (:id insight)))
      (is (= ["совет 1" "совет 2"] (:advice-to-self insight)))
      (is (= "я спокоен" (:identity insight)))))
  (testing "пустой advice отклоняется"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"Insight validation failed"
         (domains/create-insight
          @ds-atom user-id
          {:context "контекст" :category "coping" :advice_to_self []}))))
  (testing "невалидный category отклоняется"
    (is (thrown?
         clojure.lang.ExceptionInfo
         (domains/create-insight
          @ds-atom user-id
          {:context "контекст" :category "foo" :advice_to_self ["a"]})))))

(deftest test-normalize-advice
  (testing "пустые строки фильтруются, остальные тримятся"
    (let [insight (domains/create-insight
                   @ds-atom user-id
                   {:context "контекст"
                    :category "general"
                    :advice_to_self ["  breathe  " "  " "walk"]})]
      (is (= ["breathe" "walk"] (:advice-to-self insight)))))
  (testing "JSON-массив-строка (hyperscript serialization) парсится"
    (let [insight (domains/create-insight
                   @ds-atom user-id
                   {:context "контекст"
                    :category "general"
                    :advice_to_self "[\"совет 1\",\"совет 2\"]"})]
      (is (= ["совет 1" "совет 2"] (:advice-to-self insight)))))
  (testing "простая строка (один textarea) — один совет"
    (let [insight (domains/create-insight
                   @ds-atom user-id
                   {:context "контекст"
                    :category "general"
                    :advice_to_self "один совет"})]
      (is (= ["один совет"] (:advice-to-self insight))))))

(deftest test-normalize-identity
  (testing "пустая identity → nil"
    (let [insight (domains/create-insight
                   @ds-atom user-id
                   {:context "контекст"
                    :category "general"
                    :advice_to_self ["a"]
                    :identity "   "})]
      (is (nil? (:identity insight)))))
  (testing "identity без значения опциональна"
    (let [insight (domains/create-insight
                   @ds-atom user-id
                   {:context "контекст"
                    :category "general"
                    :advice_to_self ["a"]})]
      (is (nil? (:identity insight))))))

(deftest test-normalize-state-label
  (testing "state_label сохраняется как raw-строка"
    (let [insight (domains/create-insight
                   @ds-atom user-id
                   {:context "контекст"
                    :category "general"
                    :advice_to_self ["a"]
                    :state_label "anxiety"})]
      (is (= "anxiety" (:state-label insight))))))

(deftest test-list-insights
  (testing "список без фильтра"
    (domains/create-insight @ds-atom user-id {:context "a" :category "coping" :advice_to_self ["x"]})
    (domains/create-insight @ds-atom user-id {:context "b" :category "productivity" :advice_to_self ["y"]})
    (is (= 2 (count (domains/list-insights @ds-atom user-id)))))
  (testing "список с category-фильтром"
    (is (= 1 (count (domains/list-insights @ds-atom user-id "coping"))))
    (is (= 1 (count (domains/list-insights @ds-atom user-id "productivity"))))
    (is (= 0 (count (domains/list-insights @ds-atom user-id "identity"))))))

(deftest test-matching-insight
  (testing "exact match возвращает 1 инсайт"
    (domains/create-insight @ds-atom user-id {:context "a" :category "coping" :advice_to_self ["x"] :state_label "anxiety"})
    (domains/create-insight @ds-atom user-id {:context "b" :category "coping" :advice_to_self ["y"] :state_label "low"})
    (let [match (domains/matching-insight @ds-atom user-id "anxiety")]
      (is (some? match))
      (is (= "anxiety" (:state-label match))))
    (is (nil? (domains/matching-insight @ds-atom user-id "mixed")))))

(deftest test-update-field
  (testing "обновление context"
    (let [insight (domains/create-insight @ds-atom user-id {:context "старый" :category "coping" :advice_to_self ["a"]})
          updated (domains/update-field @ds-atom user-id (:id insight) :context "новый")]
      (is (= "новый" (:context updated)))))
  (testing "обновление advice (нормализация)"
    (let [insight (domains/create-insight @ds-atom user-id {:context "c" :category "coping" :advice_to_self ["a"]})
          updated (domains/update-field @ds-atom user-id (:id insight) :advice_to_self ["b" "  " "c"])]
      (is (= ["b" "c"] (:advice-to-self updated)))))
  (testing "чужой user не обновляет"
    (let [insight (domains/create-insight @ds-atom user-id {:context "c" :category "coping" :advice_to_self ["a"]})]
      (is (nil? (domains/update-field @ds-atom 999 (:id insight) :context "hack"))))))

(deftest test-delete-insight
  (testing "hard delete"
    (let [insight (domains/create-insight @ds-atom user-id {:context "c" :category "coping" :advice_to_self ["a"]})]
      (domains/delete-insight @ds-atom user-id (:id insight))
      (is (nil? (domains/get-insight @ds-atom user-id (:id insight))))))
  (testing "чужой user не удаляет"
    (let [insight (domains/create-insight @ds-atom user-id {:context "c" :category "coping" :advice_to_self ["a"]})]
      (domains/delete-insight @ds-atom 999 (:id insight))
      (is (some? (domains/get-insight @ds-atom user-id (:id insight)))))))
