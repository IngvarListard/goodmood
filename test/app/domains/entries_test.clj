(ns app.domains.entries-test
  (:require [app.domains.entries :as entries]
            [clojure.test :refer [deftest is testing]])
  (:import java.time.LocalDate))

;; Детерминированное «сегодня» — агрегация чистая, today инъектится (D4).
(def ^:private today (LocalDate/parse "2026-09-13"))

(defn- date-str
  [days-ago]
  (str (.minusDays today days-ago)))

(defn- entry
  [date & {:as fields}]
  (merge {:date date :mood-score 5} fields))

(deftest test-period-axes-single-entry
  (testing "единственная запись дня = значения записи"
    (is (= {:energy 3.0 :anxiety 7.0 :focus 2.0 :mood-score 5.0}
           (entries/period-axes
            [(entry (date-str 0) :energy 3 :anxiety 7 :focus 2 :mood-score 5)]
            :day today)))))

(deftest test-period-axes-day-equal-weights
  (testing "5 записей одного дня весят как 1 (per-day mean, не сумма)"
    (is (= 3.0 (:energy (entries/period-axes
                         (map (fn [e] (entry (date-str 0) :energy e :mood-score e))
                              [1 2 3 4 5])
                         :day today))))))

(deftest test-period-axes-week-freshness
  (testing "свежие дни весят больше: [пн 3, вт 5, сб 8] → ближе к 8 (design ≈6.7)"
    (let [r (:energy (entries/period-axes
                      [(entry (date-str 6) :energy 3)
                       (entry (date-str 5) :energy 5)
                       (entry (date-str 1) :energy 8)]
                      :week today))]
      (is (< 6.5 r 6.8) "взвешенное ≈6.66, простое среднее было бы 5.33"))))

(deftest test-period-axes-nil-axes-ignored
  (testing "nil-оси не тянут вниз; ось без непустых значений → 0"
    (let [r (entries/period-axes
             [(entry (date-str 0) :energy 4 :focus 6)
              (entry (date-str 0))]
             :day today)]
      (is (= 4.0 (:energy r)))
      (is (= 6.0 (:focus r)) "nil фокуса второй записи не уменьшает среднее")
      (is (zero? (:anxiety r)) "ни у одной записи нет тревоги"))))

(deftest test-period-axes-empty-period
  (testing "пустой период → нули по всем осям"
    (let [r (entries/period-axes
             [(entry (date-str 40) :energy 9)]
             :day today)]
      (is (= {:energy 0 :anxiety 0 :focus 0 :mood-score 0} r)))))

(deftest test-period-axes-window-sizes
  (testing "неделя = 7 дней: запись 7 дней назад не входит, 6 — входит"
    (is (zero? (:energy (entries/period-axes
                         [(entry (date-str 7) :energy 9)] :week today))))
    (is (= 9.0 (:energy (entries/period-axes
                         [(entry (date-str 6) :energy 9)] :week today)))))
  (testing "месяц = 30 дней: запись 30 дней назад не входит, 29 — входит"
    (is (zero? (:energy (entries/period-axes
                         [(entry (date-str 30) :energy 9)] :month today))))
    (is (= 9.0 (:energy (entries/period-axes
                         [(entry (date-str 29) :energy 9)] :month today))))))

(deftest test-period-axes-future-excluded
  (testing "записи с датой из будущего исключаются из любого периода"
    (let [future (str (.plusDays today 1))]
      (is (zero? (:energy (entries/period-axes
                           [(entry future :energy 9)] :day today))))
      (is (zero? (:energy (entries/period-axes
                           [(entry future :energy 9)] :week today)))))))
