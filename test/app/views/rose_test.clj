(ns app.views.rose-test
  (:require [app.views.rose :as rose]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- radar-html
  [opts]
  (str (hiccup2.core/html (rose/radar opts))))

(defn- has-point?
  [html x y]
  (boolean
   (re-find (re-pattern (str (format "%.1f" (double x))
                            "\\d*," (format "%.1f" (double y)) "\\d*"))
            html)))

(deftest radar-3-axis-renders-polygon
  (testing "3-axis radar renders polygon with correct polar coordinates"
    (let [html (radar-html {:energy 4 :anxiety 7 :focus 3})]
      (is (str/includes? html "<svg"))
      (is (str/includes? html "width=\"200\""))
      (is (str/includes? html "height=\"200\""))
      (is (str/includes? html "<polygon"))
      (is (has-point? html 100.0 68.0))
      (is (has-point? html 148.5 128.0))
      (is (has-point? html 79.2 112.0))
      (is (str/includes? html "fill=\"hsl(var(--p))\"")))))

(deftest radar-4-axis-renders-4-vertex-polygon
  (testing "4-axis radar with mood_score renders a 4-vertex polygon"
    (let [html (radar-html {:energy 4 :anxiety 7 :focus 3 :mood-score 5})]
      (is (str/includes? html "<polygon"))
      (is (has-point? html 100.0 68.0))
      (is (has-point? html 156.0 100.0))
      (is (has-point? html 100.0 124.0))
      (is (has-point? html 60.0 100.0))
      (is (str/includes? html ">5<")))))

(deftest radar-has-no-interactive-elements
  (testing "SVG contains only polygon/line/circle/text, no JS interactivity"
    (let [html (radar-html {:energy 4 :anxiety 7 :focus 3})]
      (is (str/includes? html "<line"))
      (is (str/includes? html "<circle"))
      (is (str/includes? html "<text"))
      (is (not (str/includes? html "<button")))
      (is (not (str/includes? html "<input")))
      (is (not (str/includes? html "onclick")))
      (is (not (str/includes? html "hx-"))))))

(deftest radar-renders-labels
  (testing "axis labels and values are rendered as text elements"
    (let [html (radar-html {:energy 4 :anxiety 7 :focus 3})]
      (is (str/includes? html ">4<"))
      (is (str/includes? html ">7<"))
      (is (str/includes? html ">3<")))))