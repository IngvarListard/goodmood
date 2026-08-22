(ns app.views.placeholder-test
  (:require [app.views.placeholder :as placeholder]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(deftest active-id-test
  (testing "maps nav route paths to item ids"
    (is (= :feed (placeholder/active-id "/feed")))
    (is (= :check-in (placeholder/active-id "/check-in")))
    (is (= :medications (placeholder/active-id "/medications")))
    (is (= :settings (placeholder/active-id "/settings"))))

  (testing "returns nil for removed and non-navigation paths"
    (is (nil? (placeholder/active-id "/dashboard")))
    (is (nil? (placeholder/active-id "/history")))
    (is (nil? (placeholder/active-id "/statistics")))
    (is (nil? (placeholder/active-id "/insights")))
    (is (nil? (placeholder/active-id "/entries")))
    (is (nil? (placeholder/active-id "/unknown")))
    (is (nil? (placeholder/active-id nil)))))

(deftest page-test
  (testing "renders stub content through the common layout"
    (let [html (str (hiccup2.core/html
                     (placeholder/page {:title-key :pages/under-development}
                                       {:uri "/medications"})))]
      (is (str/includes? html "<html"))
      (is (str/includes? html "Раздел в разработке"))
      (is (str/includes? html "hx-boost=\"true\""))))

  (testing "passes active item derived from the request path"
    (let [html (str (hiccup2.core/html
                     (placeholder/page {:title-key :pages/under-development}
                                       {:uri "/feed"})))]
      (is (str/includes? html "aria-current=\"page\""))
      (is (str/includes? html "fill=\"currentColor\""))))

  (testing "no active item for non-navigation path"
    (let [html (str (hiccup2.core/html
                     (placeholder/page {:title-key :pages/under-development}
                                       {:uri "/not-a-route"})))]
      (is (not (str/includes? html "aria-current"))))))