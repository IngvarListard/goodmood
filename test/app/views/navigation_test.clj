(ns app.views.navigation-test
  (:require [app.views.navigation :as nav]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- html-str
  [hiccup]
  (str (hiccup2.core/html hiccup)))

(deftest navigation-test
  (testing "mobile renders all items as links in horizontal menu"
    (let [html (html-str (nav/navigation :mobile nav/nav-items))]
      (is (= (count nav/nav-items)
             (count (re-seq #"<a " html))))
      (is (str/includes? html "menu menu-horizontal"))
      (is (not (str/includes? html "<button")))
      (doseq [{:keys [label route]} nav/nav-items]
        (is (str/includes? html label))
        (is (str/includes? html (str "href=\"" route "\""))))))

  (testing "desktop renders all items as links in vertical menu"
    (let [html (html-str (nav/navigation :desktop nav/nav-items))]
      (is (= (count nav/nav-items)
             (count (re-seq #"<a " html))))
      (is (str/includes? html "menu menu-vertical"))
      (is (str/includes? html "Good Mood"))
      (is (not (str/includes? html "<button")))
      (doseq [{:keys [label route]} nav/nav-items]
        (is (str/includes? html label))
        (is (str/includes? html (str "href=\"" route "\""))))))

  (testing "links stretch to full width of their list item"
    (let [html (html-str (nav/navigation :desktop nav/nav-items))]
      (is (str/includes? html "flex w-full"))))

  (testing "mobile active item has text-primary"
    (let [html (html-str (nav/navigation :mobile nav/nav-items {:active :dashboard}))]
      (is (str/includes? html "text-primary"))))

  (testing "desktop active item has menu-active and text-primary"
    (let [html (html-str (nav/navigation :desktop nav/nav-items {:active :check-in}))]
      (is (str/includes? html "menu-active"))
      (is (str/includes? html "text-primary"))))

  (testing "active link has aria-current page"
    (let [html (html-str (nav/navigation :mobile nav/nav-items {:active :history}))]
      (is (str/includes? html "aria-current=\"page\""))))

  (testing "no active when :active is nil"
    (let [html (html-str (nav/navigation :mobile nav/nav-items))]
      (is (not (str/includes? html "text-primary")))
      (is (not (str/includes? html "menu-active")))
      (is (not (str/includes? html "aria-current")))))

  (testing "active item uses solid icon"
    (let [html (html-str (nav/navigation :desktop nav/nav-items {:active :dashboard}))]
      (is (str/includes? html "fill=\"currentColor\""))))

  (testing "inactive items use outline icon"
    (let [html (html-str (nav/navigation :desktop nav/nav-items {:active :dashboard}))]
      (is (str/includes? html "fill=\"none\"")))))