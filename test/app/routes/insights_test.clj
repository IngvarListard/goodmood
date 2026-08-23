(ns app.routes.insights-test
  (:require [app.middleware :as mw]
            [app.routes.app :as routes]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [jsonista.core :as json]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.session.store :as session.store]))

(defonce ^:private tmp-path "/tmp/goodmood-insights-routes-test.db")

(def ^:private ds-atom (atom nil))

(def ^:private session-secret "insights-routes-test-secret-0123456789abcdef")

(def ^:private cookie-store
  (session.cookie/cookie-store {:key (mw/secret-key session-secret)}))

(def ^:private csrf-token "test-csrf-token")

(def ^:private test-identity
  {:id 1 :email "user@test.dev" :role "user" :display-name "Test User"})

(def ^:private other-identity
  {:id 2 :email "other@test.dev" :role "user" :display-name "Other User"})

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
  [request session]
  (let [sealed (session.store/write-session cookie-store nil session)]
    (assoc request :cookies {"gm-session" {:value sealed}})))

(defn- authed
  [request identity]
  (with-session request {:identity identity
                         :ring.middleware.anti-forgery/anti-forgery-token csrf-token}))

(defn- with-csrf-header
  [request]
  (assoc-in request [:headers "x-csrf-token"] csrf-token))

(defn- json-body
  [body]
  (json/write-value-as-string body))

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

(defn- get-html
  ([uri] (get-html uri test-identity))
  ([uri identity]
   (let [[path query] (str/split uri #"\?" 2)]
     ((app)
      (authed {:request-method :get
               :uri path
               :query-string query
               :headers {"accept" "text/html"}
               :body nil}
              identity)))))

(defn- post-json
  "POST с JSON-телом (как json-enc от htmx): значения — строки, CSRF в теле."
  [uri payload & [identity]]
  ((app)
   (-> {:request-method :post
        :uri uri
        :headers {"content-type" "application/json"
                  "accept" "application/json"
                  "hx-request" "true"}
        :body (java.io.ByteArrayInputStream.
               (.getBytes (json-body (assoc payload "__anti-forgery-token" csrf-token))))}
       (authed (or identity test-identity)))))

(defn- delete-request
  "DELETE с CSRF-заголовком."
  [uri]
  ((app)
   (-> {:request-method :delete
        :uri uri
        :headers {"accept" "text/html" "hx-request" "true"}
        :body nil}
       (authed test-identity)
       with-csrf-header)))

(defn- create-insight-id-from-db
  "Достать id последнего созданного инсайта из БД."
  []
  (:id (first (jdbc/execute! @ds-atom ["SELECT id FROM insights ORDER BY id DESC LIMIT 1"]
                             {:builder-fn rs/as-unqualified-maps}))))

(defn- create-insight!
  "Создать инсайт через API и вернуть его id."
  ([]
   (create-insight! {}))
  ([overrides]
   (let [response
         (post-json "/insights"
                    (merge {:context "Когда тревога 7+, не принимай решений"
                            :category "coping"
                            :advice_to_self ["дыхание 4-7-8" "текст близкому"]
                            :identity "я не своя тревога"
                            :state_label "anxiety"
                            :entry_id nil}
                           overrides))]
     (is (= 201 (:status response)) "creating an insight succeeds")
     (create-insight-id-from-db))))

(defn- insight-count
  []
  (count (jdbc/execute! @ds-atom ["SELECT * FROM insights"])))

(deftest insights-page-returns-200
  (testing "GET /insights returns the page with title and empty state"
    (let [response (get-html "/insights")
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? (content-type response) "text/html"))
      (is (str/starts-with? body "<html"))
      (is (str/includes? body ">Инсайты<"))
      (is (str/includes? body "У тебя ещё нет инсайтов")
          "shows global onboarding when no insights"))))

(deftest insights-status-401-without-auth
  (testing "GET /insights without session redirects to login"
    (let [response ((app) {:request-method :get :uri "/insights"})]
      (is (= 302 (:status response)))
      (is (str/includes? (get-in response [:headers "Location"]) "/login")))))

(deftest insights-new-returns-form
  (testing "GET /insights/new returns the form"
    (let [response (get-html "/insights/new")
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "Новый инсайт"))
      (is (str/includes? body "name=\"context\""))
      (is (str/includes? body "name=\"advice_to_self\"")))))

(deftest insights-new-preselects-state-from-query
  (testing "GET /insights/new?state_label=anxiety preselects state"
    (let [response (get-html "/insights/new?state_label=anxiety")
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (re-find #"selected[^>]*value=\"anxiety\"" body)
          "state_label option is selected"))))

(deftest create-insight-via-api
  (testing "POST /insights creates insight and returns HX-Redirect"
    (let [id (create-insight!)]
      (is (some? id))
      (is (= 1 (insight-count)))
      (let [rows (jdbc/execute! @ds-atom ["SELECT * FROM insights WHERE id = ?" id]
                                {:builder-fn rs/as-unqualified-kebab-maps})
            row (first rows)]
        (is (= "anxiety" (:state-label row)))
        (is (= "coping" (:category row)))
        (is (some? row))))))

(deftest create-insight-with-dangling-entry-id
  (testing "POST /insights with entry_id stores soft reference"
    (let [id (create-insight! {:entry_id "999"})
          row (first (jdbc/execute! @ds-atom ["SELECT entry_id FROM insights WHERE id = ?" id]
                                    {:builder-fn rs/as-unqualified-maps}))]
      (is (= 999 (:entry_id row)))
      (is (some? id)))))

(deftest create-insight-invalid-data
  (testing "POST /insights with empty advice returns validation error"
    (let [response (post-json "/insights" {:context "c"
                                           :category "coping"
                                           :advice_to_self []
                                           :state_label "anxiety"})
          body (body-text response)]
      (is (= 200 (:status response)) "domain returns error fragment")
      (is (str/includes? body "alert-warning")
          "validation error shows soft alert")
      (is (= 0 (insight-count))))))

(deftest create-insight-with-string-advice
  (testing "single-advice form sends a string; it is normalized to vector"
    (let [response (post-json "/insights" {:context "c"
                                           :category "coping"
                                           :advice_to_self "дыхание 4-7-8"
                                           :state_label "anxiety"})
          id (create-insight-id-from-db)
          rows (when id (jdbc/execute! @ds-atom ["SELECT advice_to_self FROM insights WHERE id = ?" id]
                                       {:builder-fn rs/as-unqualified-maps}))]
      (is (= 201 (:status response)))
      (is (= ["дыхание 4-7-8"] (json/read-value (:advice_to_self (first rows))))))))

(deftest show-insight-page
  (testing "GET /insights/:id shows full insight"
    (let [id (create-insight!)
          response (get-html (str "/insights/" id))
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "Контекст состояния") "context card")
      (is (str/includes? body "list-decimal") "advice as numbered list")
      (is (str/includes? body "Удалить") "delete button"))))

(deftest show-insight-404-for-foreign-user
  (testing "GET /insights/:id of another user returns 404"
    (let [id (create-insight!)
          response (get-html (str "/insights/" id) other-identity)]
      (is (= 404 (:status response))))))

(deftest edit-context-fragment
  (testing "GET /insights/:id/edit-context returns edit form"
    (let [id (create-insight!)
          response (get-html (str "/insights/" id "/edit-context"))
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "textarea"))
      (is (str/includes? body (str "hx-post=\"/insights/" id "/context\""))))))

(deftest advice-items-fragment
  (testing "GET /insights/:id/advice-items returns all advice items"
    (let [id (create-insight! {:advice_to_self ["дыхание 4-7-8" "текст близкому" "третий"]})
          response (get-html (str "/insights/" id "/advice-items"))
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body (str "advice-preview-" id)))
      (is (str/includes? body "дыхание 4-7-8"))
      (is (str/includes? body "текст близкому"))
      (is (str/includes? body "третий"))))
  (testing "404 for foreign user"
    (let [id (create-insight!)
          response (get-html (str "/insights/" id "/advice-items") other-identity)]
      (is (= 404 (:status response))))))

(deftest update-context
  (testing "POST /insights/:id/context updates and returns read block"
    (let [id (create-insight!)
          response (post-json (str "/insights/" id "/context") {:context "новый контекст"})
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "новый контекст"))
      (let [rows (jdbc/execute! @ds-atom ["SELECT context FROM insights WHERE id = ?" id]
                                {:builder-fn rs/as-unqualified-maps})]
        (is (= "новый контекст" (:context (first rows))))))))

(deftest update-advice
  (testing "POST /insights/:id/advice updates advice list"
    (let [id (create-insight!)
          response (post-json (str "/insights/" id "/advice") {:advice_to_self ["новый совет" "второй"]})
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "новый совет"))
      (let [rows (first (jdbc/execute! @ds-atom ["SELECT advice_to_self FROM insights WHERE id = ?" id]
                                       {:builder-fn rs/as-unqualified-maps}))
            advice (json/read-value (:advice_to_self rows))]
        (is (= ["новый совет" "второй"] advice))))))

(deftest delete-insight
  (testing "DELETE /insights/:id removes insight and redirects"
    (let [id (create-insight!)]
      (is (= 1 (insight-count)))
      (let [response (delete-request (str "/insights/" id))]
        (is (= 200 (:status response)))
        (is (= "/insights" (get-in response [:headers "HX-Redirect"])))
        (is (= 0 (insight-count)))))))

(deftest personal-ownership
  (testing "user B cannot see or delete user A's insights"
    (let [id (create-insight!)
          list-b (get-html "/insights" other-identity)]
      (is (= 200 (:status list-b)))
      (is (str/includes? (body-text list-b) "У тебя ещё нет инсайтов")
          "user B sees empty state, not user A's insights")
      (let [show-b (get-html (str "/insights/" id) other-identity)]
        (is (= 404 (:status show-b)))))))

(deftest advice-item-fragment
  (testing "GET /insights/advice-item returns a fragment"
    (let [response (get-html "/insights/advice-item")
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "advice-item"))
      (is (str/includes? body "name=\"advice_to_self\"")))))

(deftest feed-widget-shows-matching-insight
  (testing "/feed shows widget with matching insight when entry today exists"
    ;; создать запись сегодня со state_label anxiety (hero)
    (let [entry-resp
          (post-json "/entries" {:mood_score "3"
                                 :energy "2"
                                 :anxiety "7"
                                 :focus "2"
                                 :template "morning"
                                 :state_label "anxiety"})
          _ (is (= 201 (:status entry-resp)))
          insight-id (create-insight! {})
          feed-response (get-html "/feed")
          body (body-text feed-response)]
      (is (= 200 (:status feed-response)))
      (is (str/includes? body "Что ты сам говорил")))))

(deftest feed-widget-onboarding
  (testing "/feed shows onboarding when no matching insight for current state"
    (post-json "/entries" {:mood_score "3"
                           :energy "2"
                           :anxiety "7"
                           :focus "3"
                           :note "morning"
                           :state_label "anxiety"})
    (create-insight! {:state_label "low" :context "для другого состояния"})
    (let [feed-response (get-html "/feed")
          body (body-text feed-response)]
      (is (str/includes? body "Инсайтов для состояния")
          "onboarding shown when no label match"))))