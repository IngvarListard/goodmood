(ns app.views.layout-test
  (:require [app.i18n :as app.i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as nav]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- html-str
  [hiccup]
  (str (hiccup2.core/html hiccup)))

(deftest layout-test
  (testing "includes all CDN resources"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (is (str/includes? html layout/htmx-src))
      (is (str/includes? html layout/hyperscript-src))
      (is (str/includes? html layout/tailwind-src))
      (is (str/includes? html layout/daisyui-href))))

  (testing "sets page title"
    (let [html (html-str (layout/layout {:title "My Page"} nav/nav-items [:div]))]
      (is (str/includes? html "<title>My Page</title>"))))

  (testing "desktop navigation has correct classes"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (is (str/includes? html "hidden md:flex"))
      (is (str/includes? html "fixed left-0 top-0 h-screen w-64"))))

  (testing "mobile navigation has fixed positioning, z-index and safe-area"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (is (str/includes? html "md:hidden fixed bottom-0 inset-x-0"))
      (is (str/includes? html "z-50"))
      (is (str/includes? html "pb-[env(safe-area-inset-bottom)]"))))

  (testing "body has hx-boost for AJAX navigation"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (is (str/includes? html "hx-boost=\"true\""))))

  (testing "active item is passed to navigation"
    (let [html (html-str (layout/layout {:title "Test" :active :statistics} nav/nav-items [:div]))]
      (is (str/includes? html "menu-active"))
      (is (str/includes? html "href=\"/statistics\""))))

  (testing "main content has correct padding"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (is (str/includes? html "md:pl-64"))
      (is (str/includes? html "pb-16"))))

  (testing "includes navigation items"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (doseq [item nav/nav-items]
        (is (str/includes? html (nav/nav-label item))))))

  (testing "html lang attribute defaults to ru"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (is (str/includes? html "lang=\"ru\""))))

  (testing "lang attribute follows bound locale"
    (binding [app.i18n/*locale* :en]
      (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
        (is (str/includes? html "lang=\"en\"")))))

  (testing "renders user menu when identity is present"
    (let [request {:identity {:id 1 :display-name "Alice" :role "user"}
                   :anti-forgery-token "tok"}
          html (html-str (layout/layout {:title "Test" :request request} nav/nav-items [:div]))]
      (is (str/includes? html "Alice"))
      (is (str/includes? html "Выйти"))
      (is (str/includes? html "csrf-token"))))

  (testing "no user menu without identity"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (is (not (str/includes? html "Выйти")))))

  (testing "includes content"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div "Hello World"]))]
      (is (str/includes? html "Hello World"))))

  (testing "no JavaScript viewport detection"
    (let [html (html-str (layout/layout {:title "Test"} nav/nav-items [:div]))]
      (is (not (str/includes? html "innerWidth"))
          "Should not use JavaScript width detection"))))