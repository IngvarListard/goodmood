(ns app.db.medications-test
  (:require [app.db.medications :as db]
            [app.domains.medications :as domains]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [malli.core :as mc]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]))

(defonce ^:private tmp-path "/tmp/goodmood-medications-test.db")

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

(defn- add-medication
  ([] (add-medication {}))
  ([overrides]
   (db/create-medication!
    @ds-atom
    (merge {:user-id user-id
            :name "Препарат А"
            :dose 600
            :dose-unit "мг"
            :schedule (db/serialize-schedule ["08:00" "20:00"])
            :sensitive 0
            :notes nil}
           overrides))))

(deftest create-medication
  (testing "medication is saved with all fields and defaults"
    (let [med (add-medication {:notes "n"})]
      (is (pos? (:id med)))
      (is (= user-id (:user-id med)))
      (is (= "Препарат А" (:name med)))
      (is (= 600.0 (:dose med)))
      (is (= "мг" (:dose-unit med)))
      (is (= "[\"08:00\",\"20:00\"]" (:schedule med)))
      (is (= 1 (:active med)) "new medication is active by default")
      (is (= 0 (:sensitive med)))
      (is (= "n" (:notes med)))
      (is (not (nil? (:created-at med))))
      (is (not (nil? (:updated-at med)))))))

(deftest get-medication-scoped-to-user
  (testing "medication is retrieved by user and id only"
    (let [mine (add-medication {})]
      (db/create-medication! @ds-atom {:user-id 2
                                       :name "Препарат Д"
                                       :dose 50
                                       :dose-unit "мг"
                                       :schedule "[\"08:00\"]"})
      (is (= "Препарат А" (:name (db/get-medication @ds-atom user-id (:id mine)))))
      (is (nil? (db/get-medication @ds-atom user-id 2))
          "another user's medication is not visible")
      (is (nil? (db/get-medication @ds-atom user-id 999))
          "unknown id returns nil"))))

(deftest get-medications-by-user
  (testing "only the user's medications are returned"
    (add-medication {})
    (add-medication {:name "Препарат Г" :dose 200})
    (db/create-medication! @ds-atom {:user-id 2
                                     :name "Препарат Д"
                                     :dose 50
                                     :dose-unit "мг"
                                     :schedule "[\"08:00\"]"})
    (let [meds (db/get-medications-by-user @ds-atom user-id)]
      (is (= 2 (count meds)))
      (is (= #{"Препарат А" "Препарат Г"} (set (map :name meds)))))))

(deftest update-medication
  (testing "dose, unit, schedule and notes are updated with updated_at"
    (let [med (add-medication {})
          updated (db/update-medication!
                   @ds-atom user-id (:id med)
                   {:name "Препарат А"
                    :dose 450
                    :dose-unit "мг"
                    :schedule (db/serialize-schedule ["08:00"])
                    :sensitive 1
                    :notes "x"
                    :updated-at "2026-08-21T10:00:00"})]
      (is (= 450.0 (:dose updated)))
      (is (= "[\"08:00\"]" (:schedule updated)))
      (is (= 1 (:sensitive updated)))
      (is (= "x" (:notes updated)))
      (is (= "2026-08-21T10:00:00" (:updated-at updated)))
      (is (= "Препарат А" (:name (db/get-medication @ds-atom user-id (:id med))))))))

(deftest deactivate-and-activate
  (let [med (add-medication {})]
    (testing "deactivation sets active=0 but keeps the row"
      (let [deactivated (db/deactivate-medication! @ds-atom user-id (:id med) "2026-07-21T10:00:00")]
        (is (= 0 (:active deactivated)))
        (is (= 0 (:active (db/get-medication @ds-atom user-id (:id med)))))
        (is (= "Препарат А" (:name (db/get-medication @ds-atom user-id (:id med)))))))
    (testing "activation sets active=1"
      (db/activate-medication! @ds-atom user-id (:id med) "2026-07-22T10:00:00")
      (is (= 1 (:active (db/get-medication @ds-atom user-id (:id med))))))))

(defn- add-log
  [med-id scheduled-time]
  (db/upsert-log! @ds-atom {:user-id user-id
                            :medication-id med-id
                            :log-date "2026-08-20"
                            :scheduled-time scheduled-time
                            :status "taken"
                            :taken-at "2026-08-20T08:05:00"}))

(deftest log-upsert-updates-existing-slot
  (let [med (add-medication {})]
    (add-log (:id med) "08:00")
    (testing "second upsert overwrites instead of duplicating"
      (let [log (db/upsert-log! @ds-atom {:user-id user-id
                                          :medication-id (:id med)
                                          :log-date "2026-08-20"
                                          :scheduled-time "08:00"
                                          :status "skipped"
                                          :taken-at nil})]
        (is (= "skipped" (:status log)))
        (is (= 1 (count (db/get-logs-by-date @ds-atom user-id "2026-08-20"))))))))

(deftest get-logs-by-date
  (let [med (add-medication {})]
    (add-log (:id med) "20:00")
    (add-log (:id med) "08:00")
    (is (= 2 (count (db/get-logs-by-date @ds-atom user-id "2026-08-20"))))
    (is (= 0 (count (db/get-logs-by-date @ds-atom user-id "2026-08-21"))))))

(deftest delete-log
  (let [med (add-medication {})]
    (add-log (:id med) "08:00")
    (db/delete-log! @ds-atom user-id (:id med) "2026-08-20" "08:00")
    (is (= 0 (count (db/get-logs-by-date @ds-atom user-id "2026-08-20"))))))

(deftest unique-constraint-enforced
  (testing "UNIQUE(medication_id, log_date, scheduled_time) prevents duplicates"
    (let [med (add-medication {})]
      (add-log (:id med) "08:00")
      (add-log (:id med) "08:00")
      (is (= 1 (count (jdbc/execute! @ds-atom
                                     ["SELECT * FROM medication_logs WHERE log_date='2026-08-20'"])))))))

(deftest create-dose-change
  (testing "dose change stores previous and new dose"
    (let [med (add-medication {})]
      (db/create-dose-change! @ds-atom {:medication-id (:id med)
                                        :user-id user-id
                                        :previous-dose 600
                                        :new-dose 450})
      (let [changes (db/get-dose-changes @ds-atom user-id)]
        (is (= 1 (count changes)))
        (is (= 600.0 (:previous-dose (first changes))))
        (is (= 450.0 (:new-dose (first changes))))
        (is (not (nil? (:changed-at (first changes)))))))))

(deftest schedule-roundtrip
  (testing "parse-schedule / serialize-schedule convert between JSON and vector"
    (let [times ["08:00" "20:00"]
          json (db/serialize-schedule times)]
      (is (= "[\"08:00\",\"20:00\"]" json))
      (is (= times (db/parse-schedule json)))
      (is (= [] (db/parse-schedule nil)))
      (is (= [] (db/parse-schedule "not-json"))))))

(deftest medication-schema-validation
  (testing "required fields are enforced"
    (is (true? (mc/validate domains/medication-schema
                            {:name "Препарат А" :dose 600.0 :dose_unit "мг" :schedule "08:00"})))
    (is (false? (mc/validate domains/medication-schema
                             {:dose 600.0 :dose_unit "мг" :schedule "08:00"}))
        "missing name rejected")
    (is (false? (mc/validate domains/medication-schema
                             {:name "Препарат А" :dose_unit "мг" :schedule "08:00"}))
        "missing dose rejected")
    (is (true? (mc/validate domains/medication-schema
                            {:name "Препарат А" :dose 600.0 :dose_unit "мг" :schedule "08:00"
                             :sensitive 1 :notes "n"})))))

(deftest domain-update-tracks-dose-changes
  (testing "domain update-medication creates dose-change only when dose changes"
    (let [med (domains/create-medication @ds-atom user-id
                                         {:name "Препарат А" :dose 300 :dose_unit "мг"
                                          :schedule "08:00, 20:00"})]
      (domains/update-medication @ds-atom user-id (:id med)
                                 {:name "Препарат А" :dose 450 :dose_unit "мг"
                                  :schedule "08:00"})
      (let [changes (db/get-dose-changes @ds-atom user-id)]
        (is (= 1 (count changes)))
        (is (= 300.0 (:previous-dose (first changes))))
        (is (= 450.0 (:new-dose (first changes)))))
      (domains/update-medication @ds-atom user-id (:id med)
                                 {:name "Препарат А" :dose 450 :dose_unit "мг"
                                  :schedule "20:00"})
      (is (= 1 (count (db/get-dose-changes @ds-atom user-id)))
          "no dose-change when dose is unchanged")
      (is (= "[\"20:00\"]" (:schedule (db/get-medication @ds-atom user-id (:id med))))))))

(deftest log-schema-validation
  (is (true? (mc/validate domains/log-schema
                          {:medication_id 1 :scheduled_time "08:00" :status "taken"})))
  (is (false? (mc/validate domains/log-schema
                           {:scheduled_time "08:00" :status "taken"}))
      "missing medication-id rejected"))