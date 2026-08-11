(ns app.views.placeholder-test
  (:require [app.views.placeholder :as placeholder]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(deftest active-id-test
  (testing "maps nav route paths to item ids"
    (is (= :dashboard (placeholder/active-id "/dashboard")))
    (is (= :check-in (placeholder/active-id "/check-in")))
    (is (= :history (placeholder/active-id "/history")))
    (is (= :statistics (placeholder/active-id "/statistics")))
    (is (= :insights (placeholder/active-id "/insights")))
    (is (= :settings (placeholder/active-id "/settings"))))

  (testing "returns nil for non-navigation paths"
    (is (nil? (placeholder/active-id "/entries")))
    (is (nil? (placeholder/active-id "/unknown")))
    (is (nil? (placeholder/active-id nil)))))

(deftest page-test
  (testing "renders stub content through the common layout"
    (let [html (str (hiccup2.core/html
                     (placeholder/page {:title "Статистика"}
                                       {:uri "/statistics"})))]
      (is (str/includes? html "<html"))
      (is (str/includes? html ">Статистика<"))
      (is (str/includes? html "Раздел в разработке"))
      (is (str/includes? html "hx-boost=\"true\""))))

  (testing "passes active item derived from the request path"
    (let [html (str (hiccup2.core/html
                     (placeholder/page {:title "Дашборд"}
                                       {:uri "/dashboard"})))]
      (is (str/includes? html "aria-current=\"page\""))
      (is (str/includes? html "fill=\"currentColor\""))))

  (testing "no active item for non-navigation path"
    (let [html (str (hiccup2.core/html
                     (placeholder/page {:title "X"}
                                       {:uri "/not-a-route"})))]
      (is (not (str/includes? html "aria-current"))))))