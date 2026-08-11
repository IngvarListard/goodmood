(ns app.icons-test
  (:require [app.icons :as icons]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(deftest svg-test
  (testing "loads outline icon by default"
    (let [result (icons/svg "home")]
      (is (vector? result))
      (is (= :span (first result)))
      (is (contains? (second result) :class))))

  (testing "loads outline icon explicitly"
    (let [result (icons/svg "home" {:variant :outline})]
      (is (vector? result))
      (is (= :span (first result)))))

  (testing "loads solid icon"
    (let [result (icons/svg "home" {:variant :solid})]
      (is (vector? result))
      (is (= :span (first result)))))

  (testing "wraps icon in fixed-size flex box"
    (let [classes (get (second (icons/svg "home")) :class)]
      (is (str/includes? classes "inline-flex"))
      (is (str/includes? classes "w-6"))
      (is (str/includes? classes "h-6"))
      (is (str/includes? classes "shrink-0"))))

  (testing "applies class to span"
    (let [result (icons/svg "home" {:class "text-primary"})
          classes (get (second result) :class)]
      (is (str/starts-with? classes "inline-flex"))
      (is (str/includes? classes "text-primary"))))

  (testing "svg gets explicit width and height attributes"
    (let [result (icons/svg "home")
          raw-svg (last result)
          svg-string (str raw-svg)]
      (is (str/includes? svg-string "width=\"24\""))
      (is (str/includes? svg-string "height=\"24\""))))

  (testing "memoization - second call does not re-read disk"
    (let [call-count (atom 0)
          original-load @#'icons/load-svg
          memoized-fn (fn [name variant]
                        (swap! call-count inc)
                        (original-load name variant))]
      (with-redefs [icons/memoized-load-svg (memoize memoized-fn)]
        (icons/svg "home")
        (icons/svg "home")
        (is (= 1 @call-count)))))

  (testing "throws on unknown variant"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unknown variant"
                          (icons/svg "home" {:variant :unknown}))))

  (testing "throws on missing icon"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Icon not found"
                          (icons/svg "nonexistent"))))

  (testing "returns raw SVG in span"
    (let [result (icons/svg "home")
          raw-svg (nth result 2)]
      (is (instance? hiccup.util.RawString raw-svg)))))