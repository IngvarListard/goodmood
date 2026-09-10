(ns app.db.insights-test
  (:require [app.db.insights :as db]
            [cheshire.core :as json]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private ds-atom (atom nil))

(def user-id 1)

(use-fixtures :each #(test-helpers/with-test-db :insights ds-atom %))

(defn- add-insight
  ([] (add-insight {}))
  ([overrides]
   (db/create-insight!
    @ds-atom
    (merge {:user-id user-id
            :context "Когда тревога 7+, не принимай решений"
            :category "coping"
            :advice-to-self ["дыхание 4-7-8" "текст близкому"]
            :identity "я не своя тревога"
            :state-label "anxiety"
            :entry-id nil}
           overrides))))

(deftest test-create-insight
  (testing "инсайт создаётся со всеми полями"
    (let [insight (add-insight)]
      (is (some? (:id insight)))
      (is (= user-id (:user-id insight)))
      (is (= "coping" (:category insight)))
      (is (= ["дыхание 4-7-8" "текст близкому"] (:advice-to-self insight)))
      (is (= "я не своя тревога" (:identity insight)))
      (is (= "anxiety" (:state-label insight)))
      (is (some? (:created-at insight)))
      (is (some? (:updated-at insight))))))

(deftest test-create-standalone-insight
  (testing "standalone инсайт без entry-id и identity"
    (let [insight (add-insight {:identity nil :entry-id nil :state-label "balanced"})]
      (is (nil? (:identity insight)))
      (is (nil? (:entry-id insight)))
      (is (= "balanced" (:state-label insight))))))

(deftest test-advice-serialized-as-json
  (testing "advice_to_self хранится как JSON-массив строк"
    (let [insight (add-insight)
          raw (jdbc/execute-one!
               @ds-atom
               ["SELECT advice_to_self FROM insights WHERE id = ?" (:id insight)]
               {:builder-fn rs/as-unqualified-maps})]
      (is (string? (:advice_to_self raw)))
      (is (= ["дыхание 4-7-8" "текст близкому"]
             (json/parse-string (:advice_to_self raw)))))))

(deftest test-get-insight
  (testing "достать инсайт по id для своего user"
    (let [insight (add-insight)
          got (db/get-insight @ds-atom user-id (:id insight))]
      (is (= (:id insight) (:id got)))
      (is (= (:context insight) (:context got)))))
  (testing "чужой user не достаёт инсайт"
    (let [insight (add-insight)
          got (db/get-insight @ds-atom 999 (:id insight))]
      (is (nil? got)))))

(deftest test-get-insights
  (testing "список всех инсайтов отсортирован по updated_at DESC"
    (add-insight {:context "первый"})
    (Thread/sleep 1100)
    (add-insight {:context "второй"})
    (let [all (db/get-insights @ds-atom user-id)]
      (is (= 2 (count all)))
      (is (= "второй" (:context (first all)))))))

(deftest test-get-insights-with-category-filter
  (testing "фильтр по category"
    (add-insight {:category "coping"})
    (add-insight {:category "productivity"})
    (let [coping (db/get-insights @ds-atom user-id "coping")
          prod (db/get-insights @ds-atom user-id "productivity")
          identity-filter (db/get-insights @ds-atom user-id "identity")]
      (is (= 1 (count coping)))
      (is (= 1 (count prod)))
      (is (= 0 (count identity-filter))))))

(deftest test-get-matching-insights
  (testing "exact match по state_label, сортировка updated_at DESC, limit"
    (add-insight {:state-label "anxiety" :context "старый"})
    (Thread/sleep 1100)
    (add-insight {:state-label "anxiety" :context "новый"})
    (add-insight {:state-label "low" :context "другое состояние"})
    (let [match (db/get-matching-insights @ds-atom user-id "anxiety" 1)]
      (is (= 1 (count match)))
      (is (= "новый" (:context (first match)))))
    (let [all-match (db/get-matching-insights @ds-atom user-id "anxiety" 10)]
      (is (= 2 (count all-match))))))

(deftest test-update-insight-field-context
  (testing "обновление context и updated_at"
    (let [insight (add-insight)
          old-updated (:updated-at insight)
          updated (db/update-insight-field! @ds-atom user-id (:id insight) :context "новый контекст")]
      (is (= "новый контекст" (:context updated)))
      (is (not= old-updated (:updated-at updated))))))

(deftest test-update-insight-field-advice
  (testing "обновление advice_to_self сериализуется в JSON"
    (let [insight (add-insight)
          updated (db/update-insight-field! @ds-atom user-id (:id insight) :advice_to_self ["новый совет" "ещё один"])]
      (is (= ["новый совет" "ещё один"] (:advice-to-self updated))))))

(deftest test-update-insight-field-rejects-foreign-user
  (testing "чужой user не обновляет инсайт"
    (let [insight (add-insight)
          updated (db/update-insight-field! @ds-atom 999 (:id insight) :context "хак")]
      (is (nil? updated)))))

(deftest test-delete-insight
  (testing "hard delete по id+user"
    (let [insight (add-insight)
          res (db/delete-insight! @ds-atom user-id (:id insight))
          after (db/get-insight @ds-atom user-id (:id insight))]
      (is (some? res))
      (is (nil? after)))))

(deftest test-delete-insight-rejects-foreign-user
  (testing "чужой user не удаляет"
    (let [insight (add-insight)
          res (db/delete-insight! @ds-atom 999 (:id insight))
          after (db/get-insight @ds-atom user-id (:id insight))]
      (is (some? res))
      (is (some? after)))))

(deftest test-dangling-entry-id
  (testing "entry_id остаётся dangling — нет FK"
    (let [insight (add-insight {:entry-id 999999})
          got (db/get-insight @ds-atom user-id (:id insight))]
      (is (= 999999 (:entry-id got)))
      (is (some? got)))))
