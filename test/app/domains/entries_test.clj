(ns app.domains.entries-test
  "Тесты агрегации по дням для линейного графика ленты
   (change replace-radar-with-time-chart)."
  (:require [app.db.entries :as db]
            [app.domains.entries :as entries]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:private ds-atom (atom nil))

(def ^:private user-id 1)

(use-fixtures :each #(test-helpers/with-test-db :entries-domain ds-atom %))

(defn- days-ago
  "ISO-дата за n дней до сегодня (окно скользящее, привязка к now)."
  [n]
  (str (.minusDays (java.time.LocalDate/now) n)))

(defn- insert!
  "Прямая вставка записи (в обход домена) с дефолтами NOT NULL-колонок."
  [date & {:as fields}]
  (db/create-entry! @ds-atom
                    (merge {:user-id user-id
                            :date date
                            :activity ""
                            :effect ""
                            :mood-score 5
                            :energy 5
                            :anxiety 5}
                           fields)))

(deftest daily-series-per-day-mean
  (testing "несколько записей дня дают per-day mean по оси"
    (insert! (days-ago 0) :energy 4)
    (insert! (days-ago 0) :energy 8)
    (let [s (entries/daily-axis-series @ds-atom user-id :3d)]
      (is (= 3 (count (:dates s))))
      (is (= 6.0 (last (get-in s [:axes :energy])))))))

(deftest daily-series-missing-day-is-gap
  (testing "день без записей даёт nil (разрыв линии)"
    (insert! (days-ago 0) :energy 5)
    (let [s (entries/daily-axis-series @ds-atom user-id :3d)]
      (is (nil? (first (get-in s [:axes :energy]))))
      (is (nil? (second (get-in s [:axes :energy]))))
      (is (= 5.0 (last (get-in s [:axes :energy])))))))

(deftest daily-series-composite-inverts-anxiety-and-aggression
  (testing "composite = среднее [mood, energy, focus, 10−anxiety, 10−aggression]"
    (insert! (days-ago 0) :mood-score 6 :energy 8 :focus 6
            :anxiety 2 :aggression 4)
    (let [s (entries/daily-axis-series @ds-atom user-id :3d)]
      (is (= 6.8 (last (:composite s)))))))

(deftest daily-series-window-is-bounded
  (testing "3-дневное окно: запись 3 дня назад не входит, 2 дня назад — входит"
    (insert! (days-ago 3) :energy 9)
    (is (every? nil? (get-in (entries/daily-axis-series @ds-atom user-id :3d)
                             [:axes :energy])))
    (insert! (days-ago 2) :energy 7)
    (is (= [7.0 nil nil]
           (get-in (entries/daily-axis-series @ds-atom user-id :3d)
                   [:axes :energy])))))

(deftest daily-series-future-excluded
  (testing "записи с датой из будущего не попадают в окно"
    (insert! (str (.plusDays (java.time.LocalDate/now) 1)) :energy 9)
    (is (every? nil? (get-in (entries/daily-axis-series @ds-atom user-id :3d)
                             [:axes :energy])))))

(deftest daily-series-empty-window
  (testing "пустое окно — все оси и composite nil"
    (let [s (entries/daily-axis-series @ds-atom user-id :3d)]
      (is (= [nil nil nil] (:composite s)))
      (is (every? nil? (get-in s [:axes :mood-score]))))))

(deftest day-chunks-preserves-order
  (testing "day-chunks группирует, сохраняя порядок дней и записей"
    (let [rows [{:date "2026-09-20" :id 1}
                {:date "2026-09-20" :id 2}
                {:date "2026-09-18" :id 3}]
          chunks (entries/day-chunks rows)]
      (is (= ["2026-09-20" "2026-09-18"] (mapv :date chunks)))
      (is (= [1 2] (mapv :id (:entries (first chunks))))))))

(deftest list-entries-before-chunks-and-exhausts
  (testing "list-entries-before отдаёт ≤5 дней и next-before, затем исчерпывается"
    (dotimes [i 8]
      (insert! (days-ago (+ 10 i)) :energy 5))
    (let [r (entries/list-entries-before @ds-atom user-id (days-ago 10))]
      (is (= 5 (count (:chunks r))))
      (is (= (days-ago 15) (:next-before r))))
    (let [r (entries/list-entries-before @ds-atom user-id (days-ago 17))]
      (is (empty? (:chunks r)))
      (is (nil? (:next-before r))))))
