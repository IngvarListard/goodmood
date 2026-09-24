(ns app.db.entries-test
  (:require [app.db.entries :as db]
            [app.domains.entries :as domains]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [malli.core :as mc]))

(def ^:private ds-atom (atom nil))

(def user-id 1)

(use-fixtures :each #(test-helpers/with-test-db :entries ds-atom %))

(defn- add-entry
  [date activity mood-score]
  (db/create-entry! @ds-atom {:user-id user-id
                              :date date
                              :activity activity
                              :effect "e"
                              :mood-score mood-score
                              :energy 5
                              :anxiety 5}))

(deftest create-and-get-entry
  (testing "saved entry is retrievable with correct fields"
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-04"
                                :activity "walk"
                                :effect "calm"
                                :mood-score 7
                                :energy 8
                                :anxiety 2
                                :focus 6
                                :sleep-hours 8.0
                                :note "test note"
                                :template "evening"})
    (let [entries (db/get-entries @ds-atom user-id)]
      (is (= 1 (count entries)))
      (let [entry (first entries)]
        (is (= "2026-08-04" (:date entry)))
        (is (= "walk" (:activity entry)))
        (is (= "calm" (:effect entry)))
        (is (= 7 (:mood-score entry)))
        (is (= 8 (:energy entry)))
        (is (= 2 (:anxiety entry)))
        (is (= 6 (:focus entry)))
        (is (= 8.0 (:sleep-hours entry)))
        (is (= "test note" (:note entry)))
        (is (= "evening" (:template entry)))
        (is (= user-id (:user-id entry)))
        (is (not (nil? (:created-at entry))))))))

(deftest create-entry-core-only
  (testing "entry with only core fields (mood/energy/anxiety) is created, optional fields nil"
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-05"
                                :activity ""
                                :effect ""
                                :mood-score 5
                                :energy 5
                                :anxiety 5})
    (let [entry (first (db/get-entries @ds-atom user-id))]
      (is (= 5 (:mood-score entry)))
      (is (= 5 (:energy entry)))
      (is (= 5 (:anxiety entry)))
      (is (nil? (:focus entry)))
      (is (nil? (:sleep-hours entry)))
      (is (nil? (:note entry)))
      (is (nil? (:template entry))))))

(deftest create-entry-with-optional-fields
  (testing "entry with core + optional fields persists all values"
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-06"
                                :activity "walk"
                                :effect "calm"
                                :mood-score 7
                                :energy 8
                                :anxiety 2
                                :focus 6
                                :sleep-hours 7.5
                                :note "note text"
                                :template "morning"})
    (let [entry (first (db/get-entries @ds-atom user-id))]
      (is (= "walk" (:activity entry)))
      (is (= 6 (:focus entry)))
      (is (= 7.5 (:sleep-hours entry)))
      (is (= "note text" (:note entry)))
      (is (= "morning" (:template entry))))))

(deftest create-entry-with-aggression
  (testing "create-entry! persists aggression; without it the column is NULL"
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-10"
                                :activity "a"
                                :effect "e"
                                :mood-score 5
                                :energy 5
                                :anxiety 5
                                :aggression 6})
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-11"
                                :activity "b"
                                :effect "e"
                                :mood-score 5
                                :energy 5
                                :anxiety 5})
    (let [entries (db/get-entries @ds-atom user-id)
          with-aggression (first (filter #(= "2026-08-10" (:date %)) entries))
          without-aggression (first (filter #(= "2026-08-11" (:date %)) entries))]
      (is (= 6 (:aggression with-aggression)))
      (is (nil? (:aggression without-aggression))))))

(deftest entries-ordered-by-date-desc
  (testing "get-entries returns entries ordered by date descending"
    (add-entry "2026-08-03" "a" 5)
    (add-entry "2026-08-05" "b" 6)
    (add-entry "2026-08-04" "c" 7)
    (is (= ["2026-08-05" "2026-08-04" "2026-08-03"]
           (map :date (db/get-entries @ds-atom user-id))))))

(deftest entries-filtered-by-user
  (testing "get-entries only returns entries of the requested user"
    (add-entry "2026-08-03" "mine" 5)
    (db/create-entry! @ds-atom {:user-id 2
                                :date "2026-08-04"
                                :activity "other"
                                :effect "e"
                                :mood-score 6})
    (let [entries (db/get-entries @ds-atom user-id)]
      (is (= 1 (count entries)))
      (is (= "mine" (:activity (first entries)))))))

(deftest mood-score-range-enforced
  (testing "mood score outside 0-10 is rejected"
    (is (thrown? java.sql.SQLException
                 (add-entry "2026-08-04" "x" 11)))
    (is (thrown? java.sql.SQLException
                 (add-entry "2026-08-04" "y" -1)))))

(deftest mood-score-boundaries-accepted
  (testing "mood score 0 and 10 are accepted"
    (add-entry "2026-08-04" "a" 0)
    (add-entry "2026-08-04" "b" 10)
    (is (= 2 (count (db/get-entries @ds-atom user-id))))))

(deftest create-entry-schema-requires-core
  (testing "malli schema requires mood_score, energy, anxiety"
    (is (false? (mc/validate domains/create-entry-schema {:mood_score 5 :energy 5})))
    (is (false? (mc/validate domains/create-entry-schema {:mood_score 5 :anxiety 5})))
    (is (false? (mc/validate domains/create-entry-schema {:energy 5 :anxiety 5})))
    (is (true? (mc/validate domains/create-entry-schema {:mood_score 5 :energy 5 :anxiety 5})))))

(deftest create-entry-schema-rejects-out-of-range
  (testing "out-of-range core fields are rejected by the schema"
    (is (false? (mc/validate domains/create-entry-schema {:mood_score 15 :energy 5 :anxiety 5})))
    (is (false? (mc/validate domains/create-entry-schema {:mood_score -1 :energy 5 :anxiety 5})))
    (is (false? (mc/validate domains/create-entry-schema {:mood_score 5 :energy 11 :anxiety 5})))
    (is (false? (mc/validate domains/create-entry-schema {:mood_score 5 :energy 5 :anxiety 15})))))

(deftest create-entry-schema-accepts-optional-fields
  (testing "optional fields are accepted with values or nil"
    (is (true?
         (mc/validate domains/create-entry-schema
                      {:mood_score 5 :energy 5 :anxiety 5
                       :focus 3 :sleep_hours 7.5 :note "n"
                       :activity "a" :effect "e" :template "day"})))
    (is (true?
         (mc/validate domains/create-entry-schema
                      {:mood_score 5 :energy 5 :anxiety 5
                       :focus nil :sleep_hours nil :note nil
                       :activity nil :effect nil :template nil})))))

(deftest create-entry-domain-passes-empty-strings-for-not-null
  (testing "domain create-entry converts nil activity/effect to empty string (DB NOT NULL)"
    (domains/create-entry @ds-atom user-id {:mood_score 5 :energy 5 :anxiety 5})
    (let [entry (first (db/get-entries @ds-atom user-id))]
      (is (= "" (:activity entry)))
      (is (= "" (:effect entry)))
      (is (= 5 (:mood-score entry)))
      (is (nil? (:sleep-hours entry))))))

(deftest create-entry-with-state-label
  (testing "create-entry! stores state_label; without it, column is NULL"
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-07"
                                :activity "walk"
                                :effect "e"
                                :mood-score 5
                                :energy 8
                                :anxiety 7
                                :state-label "state/mixed"})
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-08"
                                :activity ""
                                :effect ""
                                :mood-score 5
                                :energy 4
                                :anxiety 7})
    (let [entries (db/get-entries @ds-atom user-id)
          with-label (first (filter #(= "2026-08-07" (:date %)) entries))
          without-label (first (filter #(= "2026-08-08" (:date %)) entries))]
      (is (= "state/mixed" (:state-label with-label)))
      (is (nil? (:state-label without-label)))
      (is (nil? (:state-period-id with-label)))
      (is (nil? (:state-period-id without-label))))))

(deftest get-entries-ordered-by-created-at-within-day
  (testing "get-entries orders entries by created_at descending within the same date"
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-09"
                                :activity "a"
                                :effect "e"
                                :mood-score 5
                                :energy 5
                                :anxiety 5
                                :created-at "2026-08-09 07:10:00"})
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-09"
                                :activity "b"
                                :effect "e"
                                :mood-score 6
                                :energy 5
                                :anxiety 5
                                :created-at "2026-08-09 14:15:00"})
    (let [entries (db/get-entries @ds-atom user-id)]
      (is (= ["b" "a"] (map :activity entries))))))

(deftest state-label-rules
  (testing "state-label returns correct keyword for all 6 rules (first-match-wins)"
    (is (= :state/mixed (domains/state-label {:energy 8 :anxiety 7})))
    (is (= :state/anxiety (domains/state-label {:energy 4 :anxiety 7})))
    (is (= :state/anxiety (domains/state-label {:energy 2 :anxiety 6})))
    (is (= :state/elevated (domains/state-label {:energy 9 :anxiety 2})))
    (is (= :state/elevated (domains/state-label {:energy 7 :anxiety 5})))
    (is (= :state/low (domains/state-label {:energy 2 :anxiety 3})))
    (is (= :state/low (domains/state-label {:energy 3 :anxiety 4})))
    (is (= :state/balanced (domains/state-label {:energy 5 :anxiety 4})))
    (is (= :state/balanced (domains/state-label {:energy 6 :anxiety 5})))
    (is (= :state/neutral (domains/state-label {:energy 5 :anxiety 2})))))

(deftest state-label-ignores-focus
  (testing "focus axis does not affect state-label"
    (is (= :state/anxiety (domains/state-label {:energy 4 :anxiety 7 :focus 10})))
    (is (= :state/elevated (domains/state-label {:energy 9 :anxiety 2 :focus 1})))))

(deftest state-label-nil-axes-return-neutral
  (testing "entries without axis values (pre-migration 004) do not crash and classify as neutral"
    (is (= :state/neutral (domains/state-label {:energy nil :anxiety nil})))
    (is (= :state/neutral (domains/state-label {:energy 5 :anxiety nil})))
    (is (= :state/neutral (domains/state-label {:energy nil :anxiety 5})))
    (is (= :state/neutral (domains/state-label {})))))
(deftest get-entries-since-includes-boundary
  (testing "get-entries-since включает границу и сортирует свежие первыми"
    (add-entry "2026-09-18" "a" 5)
    (add-entry "2026-09-20" "b" 5)
    (add-entry "2026-09-17" "c" 5)
    (is (= ["2026-09-20" "2026-09-18"]
           (mapv :date (db/get-entries-since @ds-atom user-id "2026-09-18"))))))

(deftest get-entries-before-is-strict
  (testing "get-entries-before строго раньше и в порядке date desc"
    (add-entry "2026-09-10" "a" 5)
    (add-entry "2026-09-12" "b" 5)
    (add-entry "2026-09-12" "c" 5)
    (is (= ["2026-09-10"]
           (mapv :date (db/get-entries-before @ds-atom user-id "2026-09-12"))))))

(deftest count-entries-counts-all
  (testing "count-entries считает все записи пользователя"
    (add-entry "2026-09-01" "a" 5)
    (add-entry "2026-09-02" "b" 5)
    (is (= 2 (db/count-entries @ds-atom user-id)))
    (is (= 0 (db/count-entries @ds-atom 999)))))

(deftest get-latest-entry-returns-newest
  (testing "get-latest-entry возвращает последнюю по дате"
    (add-entry "2026-09-01" "a" 5)
    (add-entry "2026-09-05" "b" 5)
    (is (= "2026-09-05" (:date (db/get-latest-entry @ds-atom user-id))))
    (is (nil? (db/get-latest-entry @ds-atom 999)))))
