(ns app.db.notification-settings-test
  (:require [app.db.notification-settings :as db]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]))

(defonce ^:private tmp-path "/tmp/goodmood-notif-db-test.db")

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

(deftest test-get-or-default-seeds-three-slots
  (testing "первый вызов get-or-default создаёт 3 дефолтных слота"
    (let [settings (db/get-or-default @ds-atom user-id)]
      (is (= 3 (count settings)))
      (is (= #{"morning" "midday" "evening"}
             (set (map :slot settings))))
      (is (every? #{1} (map :enabled settings)))
      (is (= "08:00" (:time (first (filter #(= "morning" (:slot %)) settings)))))
      (is (= "13:00" (:time (first (filter #(= "midday" (:slot %)) settings)))))
      (is (= "19:00" (:time (first (filter #(= "evening" (:slot %)) settings))))))))

(deftest test-get-or-default-is-idempotent
  (testing "повторный вызов не плодит дубли"
    (db/get-or-default @ds-atom user-id)
    (db/get-or-default @ds-atom user-id)
    (let [rows (jdbc/execute! @ds-atom ["SELECT * FROM user_notification_settings WHERE user_id = ?" user-id])]
      (is (= 3 (count rows))))))

(deftest test-upsert-updates-slots
  (testing "upsert обновляет enabled/time трёх слотов"
    (db/get-or-default @ds-atom user-id)
    (db/upsert! @ds-atom user-id
                [{:slot "morning" :time "07:30" :enabled false}
                 {:slot "midday" :time "12:15" :enabled true}
                 {:slot "evening" :time "18:45" :enabled true}])
    (let [settings (db/get-or-default @ds-atom user-id)
          by-slot (into {} (map (juxt :slot identity) settings))]
      (is (= "07:30" (:time (get by-slot "morning"))))
      (is (= 0 (:enabled (get by-slot "morning"))))
      (is (= "12:15" (:time (get by-slot "midday")))))))

(deftest test-set-summary-shown
  (testing "set-summary-shown! обновляет last_summary_date для evening"
    (db/get-or-default @ds-atom user-id)
    (db/set-summary-shown! @ds-atom user-id "2026-08-23")
    (let [evening (first (filter #(= "evening" (:slot %))
                                 (db/get-or-default @ds-atom user-id)))]
      (is (= "2026-08-23" (:last-summary-date evening))))))

(deftest test-set-slot-shown
  (testing "set-slot-shown! обновляет last_slot_shown для конкретного слота"
    (db/get-or-default @ds-atom user-id)
    (db/set-slot-shown! @ds-atom user-id "morning" "2026-08-23")
    (let [morning (first (filter #(= "morning" (:slot %))
                                 (db/get-or-default @ds-atom user-id)))]
      (is (= "2026-08-23" (:last-slot-shown morning)))
      (let [evening (first (filter #(= "evening" (:slot %))
                                   (db/get-or-default @ds-atom user-id)))]
        (is (nil? (:last-slot-shown evening)))))))