(ns app.db.entries-test
  (:require [app.db.entries :as db]
            [clojure.test :refer [deftest is testing use-fixtures]]
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
                              :mood-score mood-score}))

(deftest create-and-get-entry
  (testing "saved entry is retrievable with correct fields"
    (db/create-entry! @ds-atom {:user-id user-id
                                :date "2026-08-04"
                                :activity "walk"
                                :effect "calm"
                                :mood-score 7
                                :sleep-hours 8.0})
    (let [entries (db/get-entries @ds-atom user-id)]
      (is (= 1 (count entries)))
      (let [entry (first entries)]
        (is (= "2026-08-04" (:date entry)))
        (is (= "walk" (:activity entry)))
        (is (= "calm" (:effect entry)))
        (is (= 7 (:mood-score entry)))
        (is (= 8.0 (:sleep-hours entry)))
        (is (= user-id (:user-id entry)))
        (is (not (nil? (:created-at entry))))))))

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