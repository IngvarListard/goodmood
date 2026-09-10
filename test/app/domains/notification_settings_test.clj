(ns app.domains.notification-settings-test
  (:require [app.domains.notification-settings :as dom]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:private ds-atom (atom nil))

(def user-id 1)

(use-fixtures :each #(test-helpers/with-test-db :notif-domain ds-atom %))

(deftest test-get-settings-defaults
  (testing "get-settings возвращает 3 дефолтных слота"
    (let [settings (dom/get-settings @ds-atom user-id)]
      (is (= 3 (count settings)))
      (is (= #{"morning" "midday" "evening"} (set (map :slot settings)))))))

(deftest test-update-from-map-form
  (testing "обновление через форму-мапу (time_*/enabled_*)"
    (dom/get-settings @ds-atom user-id)
    (let [updated (dom/update-settings
                   @ds-atom user-id
                   {:enabled_morning nil :time_morning "07:00"
                    :enabled_midday "on" :time_midday "12:00"
                    :enabled_evening nil :time_evening "18:00"})
          by-slot (into {} (map (juxt :slot identity) updated))]
      (is (= "07:00" (:time (get by-slot "morning"))))
      (is (= 0 (:enabled (get by-slot "morning"))))
      (is (= 1 (:enabled (get by-slot "midday")))))))

(deftest test-update-from-vector
  (testing "обновление вектором maps {:slot :time :enabled}"
    (dom/get-settings @ds-atom user-id)
    (let [updated (dom/update-settings @ds-atom user-id
                                       [{:slot "morning" :time "09:30" :enabled true}])
          morning (first (filter #(= "morning" (:slot %)) updated))]
      (is (= "09:30" (:time morning)))
      (is (= 1 (:enabled morning))))))

(deftest test-invalid-time-rejected
  (testing "невалидное время (25:00) отклоняется с ошибками"
    (try
      (dom/update-settings @ds-atom user-id
                           [{:slot "morning" :time "25:00" :enabled true}])
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (seq (-> e ex-data :errors)))))))

(deftest test-invalid-slot-rejected
  (testing "слот вне enum отклоняется"
    (try
      (dom/update-settings @ds-atom user-id
                           [{:slot "oops" :time "08:00" :enabled true}])
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (seq (-> e ex-data :errors)))))))