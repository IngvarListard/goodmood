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

(deftest get-entries-returns-200
  (testing "GET /entries returns 200 with stored entries"
    (post-entries {:mood_score 7 :energy 8 :anxiety 2 :sleep_hours 8.0})
    (post-entries {:mood_score 6 :energy 3 :anxiety 7 :sleep_hours 7.5})
    (let [response (get-entries)]
      (is (= 200 (:status response)))
      (let [entries (response-body response)]
        (is (= 2 (count entries)))
        (is (= #{8 3} (set (map :energy entries))))))))

(deftest get-entries-within-time-limit
  (testing "GET /entries with up to 1000 entries responds within 200ms"
    (dotimes [i 1000]
      (db/create-entry! @ds-atom {:user-id 1
                                  :date "2026-08-04"
                                  :activity (str "a" i)
                                  :effect "e"
                                  :mood-score 5}))
    (let [handler (app)
          start (System/nanoTime)
          response (handler (authed {:request-method :get
                                     :uri "/entries"
                                     :headers {"accept" "application/json"}
                                     :body nil}))
          elapsed-ms (/ (- (System/nanoTime) start) 1e6)]
      (is (= 200 (:status response)))
      (is (< elapsed-ms 200) (str "GET /entries took " elapsed-ms "ms")))))

(deftest get-entries-html-returns-page
  (testing "GET /entries with text/html accept returns HTML page with form and empty state"
    (let [response (get-entries-html)]
      (is (= 200 (:status response)))
      (is (some? (content-type response)))
      (is (str/includes? (content-type response) "text/html"))
      (let [body (body-text response)]
        (is (str/starts-with? body "<html"))
        (is (str/includes? body ">Новая запись"))
        (is (str/includes? body "id=\"form-error\""))
        (is (str/includes? body "id=\"entries-list\""))
        (is (str/includes? body "Записей пока нет"))))))

(deftest get-entries-html-lists-entries
  (testing "GET /entries html page shows stored entries in the list"
    (post-entries {:mood_score 7 :energy 8 :anxiety 2 :sleep_hours 8.0})
    (post-entries {:mood_score 6 :energy 3 :anxiety 7 :sleep_hours 7.5})
    (let [body (body-text (get-entries-html))]
      (is (str/includes? body "Настроение 7/10"))
      (is (str/includes? body "Энергия 3/10"))
      (is (not (str/includes? body "Записей пока нет"))))))

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

(deftest nav-placeholder-routes-return-pages
  (testing "each navigation route returns a 200 HTML page with the item label as heading"
    (doseq [[uri label] [["/dashboard" "Дашборд"]
                         ["/check-in" "Чек-ин"]
                         ["/history" "История"]
                         ["/statistics" "Статистика"]
                         ["/insights" "Инсайты"]
                         ["/settings" "Настройки"]]]
      (let [response (get-html-page uri)]
        (is (= 200 (:status response)) uri)
        (is (str/includes? (content-type response) "text/html"))
        (let [body (body-text response)]
          (is (str/starts-with? body "<html"))
          (is (str/includes? body (str ">" label "<")))
          (is (not (str/includes? body "Новая запись"))
              "Placeholder pages must not contain the entry form"))))))

(deftest nav-placeholder-marks-only-matching-item-active
  (testing "each route marks only its own nav item active (in both nav variants)"
    (doseq [[uri id-label] [["/dashboard" "Дашборд"]
                            ["/check-in" "Чек-ин"]
                            ["/history" "История"]
                            ["/statistics" "Статистика"]
                            ["/insights" "Инсайты"]
                            ["/settings" "Настройки"]]]
      (let [body (body-text (get-html-page uri))
            active-links (re-seq #"<a[^>]*aria-current=\"page\"[^>]*>" body)
            active-hrefs (map #(second (re-find #"href=\"([^\"]+)\"" %))
                              active-links)]
        (is (= 2 (count active-links))
            (str uri ": expected two active links (mobile + desktop nav)"))
        (is (= [uri uri] active-hrefs)
            (str uri ": active links must point to " id-label))))))

(deftest settings-page-shows-user-and-language
  (testing "/settings shows user info, language switch and logout"
    (let [body (body-text (get-html-page "/settings"))]
      (is (str/includes? body "user@test.dev"))
      (is (str/includes? body "Test User"))
      (is (str/includes? body "Выйти")))))