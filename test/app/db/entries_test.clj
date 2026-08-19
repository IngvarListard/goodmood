(ns app.db.entries-test
  (:require [app.db.entries :as db]
            [app.domains.entries :as domains]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [malli.core :as mc]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]))

(defonce ^:private tmp-path "/tmp/goodmood-entries-test.db")

(def ^:private ds-atom (atom nil))

(defn- migrate! [ds]
  (migratus/migrate {:store :database
                     :migration-dir "migrations"
                     :db {:datasource ds}}))

(def user-id 1)

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