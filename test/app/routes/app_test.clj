(ns app.routes.app-test
  (:require [app.db.entries :as db]
            [app.middleware :as mw]
            [app.routes.app :as routes]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [jsonista.core :as json]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.session.store :as session.store]))

(defonce ^:private tmp-path "/tmp/goodmood-routes-test.db")

(def ^:private ds-atom (atom nil))

(def ^:private session-secret "routes-test-secret-0123456789abcdef")

(def ^:private cookie-store
  (session.cookie/cookie-store {:key (mw/secret-key session-secret)}))

(def ^:private csrf-token "test-csrf-token")

(def ^:private test-identity
  {:id 1 :email "user@test.dev" :role "user" :display-name "Test User"})

(defn- migrate! [ds]
  (migratus/migrate {:store :database
                     :migration-dir "migrations"
                     :db {:datasource ds}}))

(defn with-test-db
  [f]
  (let [file (java.io.File. tmp-path)]
    (.delete file)
    (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname tmp-path})
          conn (jdbc/get-connection ds)]
      (.setAutoCommit conn true)
      (try
        (migrate! ds)
        (reset! ds-atom ds)
        (f)
        (finally
          (reset! ds-atom nil)
          (.close conn)
          (.delete file))))))

(use-fixtures :each with-test-db)

(defn- app []
  (routes/->app @ds-atom session-secret))

(defn- with-session
  "Attach a sealed gm-session cookie to the request for the given session map."
  [request session]
  (let [sealed (session.store/write-session cookie-store nil session)]
    (assoc request :cookies {"gm-session" {:value sealed}})))

(defn- authed
  "Attach an authenticated session (identity + CSRF token) to the request."
  [request]
  (with-session request {:identity test-identity
                         :ring.middleware.anti-forgery/anti-forgery-token csrf-token}))

(defn- with-csrf-header
  [request]
  (assoc-in request [:headers "x-csrf-token"] csrf-token))

(defn- json-body
  [body]
  (json/write-value-as-string body))

(defn- post-entries
  [payload]
  ((app)
   (-> {:request-method :post
        :uri "/entries"
        :headers {"content-type" "application/json" "accept" "application/json"}
        :body (java.io.ByteArrayInputStream. (.getBytes (json-body payload)))}
       authed
       with-csrf-header)))

(defn- get-entries
  []
  ((app)
   (authed {:request-method :get
            :uri "/entries"
            :headers {"accept" "application/json"}
            :body nil})))

(defn- post-entries-hx
  [payload]
  ((app)
   (-> {:request-method :post
        :uri "/entries"
        :headers {"content-type" "application/json"
                  "accept" "application/json"
                  "hx-request" "true"}
        :body (java.io.ByteArrayInputStream. (.getBytes (json-body payload)))}
       authed
       with-csrf-header)))

(defn- post-entries-hx-strings
  [payload]
  ((app)
   (-> {:request-method :post
        :uri "/entries"
        :headers {"content-type" "application/json"
                  "accept" "text/html"
                  "hx-request" "true"}
        :body (java.io.ByteArrayInputStream. (.getBytes (json-body payload)))}
       authed
       with-csrf-header)))

(defn- get-entries-html
  []
  ((app)
   (authed {:request-method :get
            :uri "/entries"
            :headers {"accept" "text/html"}
            :body nil})))

(defn- get-html-page
  [uri]
  ((app)
   (authed {:request-method :get
            :uri uri
            :headers {"accept" "text/html"}
            :body nil})))

(defn- body-text
  [response]
  (let [body (:body response)]
    (if (instance? java.io.InputStream body)
      (slurp body)
      (str body))))

(defn- content-type
  [response]
  (or (get-in response [:headers "content-type"])
      (get-in response [:headers "Content-Type"])))

(def ^:private keyword-keys
  (json/object-mapper {:decode-key-fn true}))

(defn- response-body
  [response]
  (json/read-value (:body response) keyword-keys))

(deftest create-entry-returns-201
  (testing "POST /entries with valid body returns 201 and created entry"
    (let [response (post-entries {:mood_score 7
                                  :energy 8
                                  :anxiety 2
                                  :sleep_hours 8.0})]
      (is (= 201 (:status response)))
      (let [entry (response-body response)]
        (is (= 7 (:mood-score entry)))
        (is (= 8 (:energy entry)))
        (is (= 2 (:anxiety entry)))
        (is (= 8.0 (:sleep-hours entry)))
        (is (= 1 (:user-id entry)) "created entry is assigned to the authenticated user")
        (is (pos? (:id entry)))
        (is (not (nil? (:created-at entry))))))))

(deftest create-entry-rejects-out-of-range-mood
  (testing "POST /entries with mood_score 15 returns 400 with validation error"
    (let [response (post-entries {:activity "walk"
                                  :effect "calm"
                                  :mood_score 15})]
      (is (= 400 (:status response)))
      (is (some? (get-in (response-body response) [:errors :mood_score]))))))

(deftest create-entry-rejects-out-of-range-energy
  (testing "POST /entries with energy 11 returns 400 with validation error"
    (let [response (post-entries {:mood_score 5
                                  :energy 11
                                  :anxiety 5})]
      (is (= 400 (:status response)))
      (is (some? (get-in (response-body response) [:errors :energy]))))))

(deftest create-entry-rejects-missing-field
  (testing "POST /entries without required core field returns 400 with validation error"
    (let [response (post-entries {:mood_score 5 :energy 5})]
      (is (= 400 (:status response)))
      (is (some? (get-in (response-body response) [:errors :anxiety]))))))

(deftest get-entries-redirects-to-feed
  (testing "GET /entries returns 308 redirect to /feed (list replaced by feed)"
    (post-entries {:mood_score 7 :energy 8 :anxiety 2 :sleep_hours 8.0})
    (let [response (get-entries)]
      (is (= 308 (:status response)))
      (is (= "/feed" (get-in response [:headers "Location"]))))))

(deftest get-entries-html-redirects-to-feed
  (testing "GET /entries with text/html accept redirects to /feed"
    (let [response (get-entries-html)]
      (is (= 308 (:status response)))
      (is (= "/feed" (get-in response [:headers "Location"]))))))

(deftest feed-page-returns-html
  (testing "GET /feed returns 200 HTML page"
    (let [response (get-html-page "/feed")]
      (is (= 200 (:status response)))
      (is (str/includes? (content-type response) "text/html"))
      (let [body (body-text response)]
        (is (str/includes? body ">Лента<"))))))

(deftest feed-page-empty-state
  (testing "GET /feed with no entries shows onboarding + link to /check-in"
    (let [body (body-text (get-html-page "/feed"))]
      (is (str/includes? body "Как ты? Создай первую запись"))
      (is (str/includes? body "href=\"/check-in\"")))))

(deftest feed-page-hero-card-with-radar
  (testing "GET /feed with today's entries shows hero card with SVG radar"
    (post-entries {:mood_score 5 :energy 8 :anxiety 7 :focus 3})
    (let [body (body-text (get-html-page "/feed"))]
      (is (str/includes? body "<svg"))
      (is (str/includes? body "Роза ветров"))
      (is (str/includes? body ">смешанное<")))))

(deftest feed-page-groups-past-days
  (testing "GET /feed groups past-day entries under date headers"
    (db/create-entry! @ds-atom {:user-id 1
                                :date "2026-08-20"
                                :activity ""
                                :effect "e"
                                :mood-score 5
                                :energy 5
                                :anxiety 5
                                :created-at "2026-08-20 12:00:00"})
    (let [body (body-text (get-html-page "/feed"))]
      (is (str/includes? body "августа")))))

(deftest root-redirects-authenticated-to-feed
  (testing "GET / for authenticated user redirects to /feed"
    (let [response (get-html-page "/")]
      (is (= 302 (:status response)))
      (is (= "/feed" (get-in response [:headers "Location"]))))))

(deftest root-health-check-ok
  (testing "GET / without identity returns OK (health check)"
    (let [response ((app) {:request-method :get
                           :uri "/"
                           :headers {"accept" "text/plain"}
                           :body nil})]
      (is (= 200 (:status response)))
      (is (= "OK" (body-text response))))))

(deftest check-in-page-returns-form
  (testing "GET /check-in returns 200 HTML form with back link to /feed"
    (let [response (get-html-page "/check-in")]
      (is (= 200 (:status response)))
      (let [body (body-text response)]
        (is (str/includes? body ">Новая запись"))
        (is (str/includes? body "href=\"/feed\""))
        (is (str/includes? body "id=\"form-error\""))
        (is (str/includes? body "name=\"mood_score\""))
        (is (str/includes? body "name=\"energy\""))
        (is (str/includes? body "name=\"anxiety\""))))))

(deftest removed-placeholder-routes-return-404-or-redirect
  (testing "removed placeholder routes are no longer available"
    (doseq [uri ["/dashboard" "/history" "/statistics"]]
      (let [response (get-html-page uri)]
        (is (not= 200 (:status response)) uri)))))

(deftest insights-route-returns-200
  (testing "GET /insights is a real Phase 3 route (not a placeholder)"
    (let [response (get-html-page "/insights")]
      (is (= 200 (:status response)))
      (let [body (body-text response)]
        (is (str/includes? body "Инсайты"))))))

(deftest nav-items-are-five
  (testing "each route marks only its own nav item active (in both nav variants)"
    (doseq [[uri id-label] [["/feed" "Лента"]
                            ["/check-in" "Новая запись"]
                            ["/insights" "Инсайты"]
                            ["/medications" "Медикаменты"]
                            ["/settings" "Настройки"]]]
      (let [body (body-text (get-html-page uri))
            active-links (re-seq #"<a[^>]*aria-current=\"page\"[^>]*>" body)
            active-hrefs (map #(second (re-find #"href=\"([^\"]+)\"" %))
                              active-links)]
        (is (= 2 (count active-links))
            (str uri ": expected two active links (mobile + desktop nav)"))
        (is (= [uri uri] active-hrefs)
            (str uri ": active links must point to " id-label))))))

(deftest post-hx-returns-html-fragment
  (testing "POST /entries with HX-Request returns 201 HTML li fragment"
    (let [response (post-entries-hx {:mood_score 7
                                      :energy 8
                                      :anxiety 2
                                      :sleep_hours 8.0})]
      (is (= 201 (:status response)))
      (is (str/includes? (content-type response) "text/html"))
      (let [body (body-text response)]
        (is (str/starts-with? body "<li"))
        (is (str/includes? body "hx-swap-oob=\"afterbegin:#entries-list\""))
        (is (str/includes? body "Настроение 7/10"))
        (is (str/includes? body "Энергия 8/10")))
      (is (= 1 (count (db/get-entries @ds-atom 1)))))))

(deftest post-hx-string-values-still-accepted
  (testing "POST /entries via htmx json-enc sends string values and blank optional fields"
    (let [response (post-entries-hx-strings {:mood_score "7"
                                             :energy "8"
                                             :anxiety "2"
                                             :focus ""
                                             :sleep_hours ""})]
      (is (= 201 (:status response)))
      (let [entry (first (db/get-entries @ds-atom 1))]
        (is (= 7 (:mood-score entry)))
        (is (= 8 (:energy entry)))
        (is (nil? (:sleep-hours entry)))
        (is (nil? (:focus entry)))))))

(deftest post-hx-validation-error-returns-html
  (testing "POST /entries with HX-Request and out-of-range mood_score returns 400 HTML fragment"
    (let [response (post-entries-hx {:mood_score 15
                                     :energy 5
                                     :anxiety 5})]
      (is (= 400 (:status response)))
      (is (str/includes? (content-type response) "text/html"))
      (let [body (body-text response)]
        (is (str/starts-with? body "<div"))
        (is (str/includes? body "alert alert-warning"))
        (is (not (str/includes? body "alert alert-error")))
        (is (str/includes? body "mood_score")))
      (is (= 0 (count (db/get-entries @ds-atom 1)))))))

(deftest post-json-validation-still-returns-json
  (testing "POST /entries without HX-Request still returns 400 JSON validation error"
    (let [response (post-entries {:mood_score 15 :energy 5 :anxiety 5})]
      (is (= 400 (:status response)))
      (let [body (response-body response)]
        (is (some? (get-in body [:errors :mood_score])))))))

(deftest settings-page-shows-user-and-language
  (testing "/settings shows user info, language switch and logout"
    (let [body (body-text (get-html-page "/settings"))]
      (is (str/includes? body "user@test.dev"))
      (is (str/includes? body "Test User"))
      (is (str/includes? body "Выйти")))))