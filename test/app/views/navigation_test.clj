(ns app.views.navigation-test
  (:require [app.i18n :as app.i18n]
            [app.views.navigation :as nav]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- html-str
  [hiccup]
  (str (hiccup2.core/html hiccup)))

(deftest navigation-test
  (testing "nav-items contains exactly 5 items (Phase 3: /insights restored)"
    (is (= 5 (count nav/nav-items)))
    (is (= ["/feed" "/check-in" "/insights" "/medications" "/settings"]
           (map :route nav/nav-items))))

  (testing "mobile renders all items as links in horizontal menu"
    (let [html (html-str (nav/navigation :mobile nav/nav-items))]
      (is (= (count nav/nav-items)
             (count (re-seq #"<a " html))))
      (is (str/includes? html "menu menu-horizontal"))
      (is (not (str/includes? html "<button")))
      (doseq [item nav/nav-items]
        (is (str/includes? html (nav/nav-label item)))
        (is (str/includes? html (str "href=\"" (:route item) "\""))))))

  (testing "desktop renders all items as links in vertical menu"
    (let [html (html-str (nav/navigation :desktop nav/nav-items))]
      (is (= (count nav/nav-items)
             (count (re-seq #"<a " html))))
      (is (str/includes? html "menu menu-vertical"))
      (is (str/includes? html "Good Mood"))
      (is (not (str/includes? html "<button")))
      (doseq [item nav/nav-items]
        (is (str/includes? html (nav/nav-label item)))
        (is (str/includes? html (str "href=\"" (:route item) "\""))))))

  (testing "links stretch to full width of their list item"
    (let [html (html-str (nav/navigation :desktop nav/nav-items))]
      (is (str/includes? html "flex w-full"))))

  (testing "mobile active item has text-primary"
    (let [html (html-str (nav/navigation :mobile nav/nav-items {:active :feed}))]
      (is (str/includes? html "text-primary"))))

  (testing "desktop active item has menu-active and text-primary"
    (let [html (html-str (nav/navigation :desktop nav/nav-items {:active :check-in}))]
      (is (str/includes? html "menu-active"))
      (is (str/includes? html "text-primary"))))

  (testing "active link has aria-current page"
    (let [html (html-str (nav/navigation :mobile nav/nav-items {:active :feed}))]
      (is (str/includes? html "aria-current=\"page\""))))

  (testing "no active when :active is nil"
    (let [html (html-str (nav/navigation :mobile nav/nav-items))]
      (is (not (str/includes? html "text-primary")))
      (is (not (str/includes? html "menu-active")))
      (is (not (str/includes? html "aria-current")))))

  (testing "active item uses solid icon"
    (let [html (html-str (nav/navigation :desktop nav/nav-items {:active :feed}))]
      (is (str/includes? html "fill=\"currentColor\""))))

  (testing "inactive items use outline icon"
    (let [html (html-str (nav/navigation :desktop nav/nav-items {:active :feed}))]
      (is (str/includes? html "fill=\"none\"")))))

(deftest nav-label-test
  (testing "nav-label translates the item label-key for the default locale"
    (is (= "Лента" (nav/nav-label {:label-key :nav/feed})))
    (is (= "Настройки" (nav/nav-label {:label-key :nav/settings})))))

(deftest english-labels-test
  (testing "labels are translated into English when locale is bound to :en"
    (binding [app.i18n/*locale* :en]
      (let [html (html-str (nav/navigation :desktop nav/nav-items))]
        (is (str/includes? html "Feed"))
        (is (str/includes? html "Settings"))
        (is (not (str/includes? html "Лента")))))))