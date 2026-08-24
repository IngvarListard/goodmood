(ns app.routes.state-periods-test
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

(defonce ^:private tmp-path "/tmp/goodmood-state-periods-routes-test.db")

(def ^:private ds-atom (atom nil))

(def ^:private session-secret "state-periods-test-secret-0123456789abcdef")

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

(defn- with-session [request session]
  (let [sealed (session.store/write-session cookie-store nil session)]
    (assoc request :cookies {"gm-session" {:value sealed}})))

(defn- authed
  "Авторизованная сессия с identity и CSRF-токеном."
  [request identity]
  (with-session request {:identity identity
                         :ring.middleware.anti-forgery/anti-forgery-token csrf-token}))

(defn- body-text
  [response]
  (let [body (:body response)]
    (if (instance? java.io.InputStream body)
      (slurp body)
      (str body))))

(defn- post-json-form
  "POST с JSON-телом (как htmx json-enc), CSRF в теле."
  [uri payload & [identity]]
  ((app)
   (-> {:request-method :post
        :uri uri
        :headers {"content-type" "application/json"
                  "accept" "text/html"
                  "hx-request" "true"}
        :body (java.io.ByteArrayInputStream.
               (.getBytes (json/write-value-as-string
                           (assoc payload "__anti-forgery-token" csrf-token))))}
       (authed (or identity test-identity)))))

(defn- post-entries
  "Создать запись через /entries (check-in) как пользователь 1."
  [payload]
  ((app)
   (authed {:request-method :post
            :uri "/entries"
            :headers {"content-type" "application/json"
                      "accept" "application/json"
                      "x-csrf-token" csrf-token}
            :body (java.io.ByteArrayInputStream.
                   (.getBytes (json/write-value-as-string payload)))}
           test-identity)))

(defn- get-html
  [uri & [identity]]
  (let [[path query] (str/split uri #"\?" 2)]
    ((app)
     (authed {:request-method :get
              :uri path
              :query-string query
              :headers {"accept" "text/html"}
              :body nil}
             (or identity test-identity)))))

(defn- first-row
  [sql & params]
  (first (jdbc/execute! @ds-atom (into [sql] params)
                        {:builder-fn rs/as-unqualified-maps})))

(deftest test-period-start-requires-auth
  (testing "POST /periods/start без авторизации отклоняется"
    (let [response ((app) {:request-method :post :uri "/periods/start"
                           :headers {"content-type" "application/json" "accept" "text/html"}
                           :body (java.io.ByteArrayInputStream. (.getBytes "{}"))})]
      (is (some? (:status response)))
      (is (not= 200 (:status response))))))

(deftest test-period-start-creates-open-period
  (testing "POST /periods/start создаёт период со started_at и без ended_at"
    (let [response (post-json-form "/periods/start" {:label "спад"})]
      (is (some? (and (>= (:status response) 200) (<= (:status response) 299)))
          "старт периода возвращает успешный статус")
      (let [row (first-row "SELECT started_at, ended_at FROM state_periods")]
        (is (some? (:started_at row)) "period создан со started_at")
        (is (nil? (:ended_at row)) "открытый период — ended_at пуст")))))

(deftest test-period-end-closes-period
  (testing "POST /periods/:id/end заполняет ended_at у открытого периода"
    (post-json-form "/periods/start" {:label "спад"})
    (let [id (:id (first-row "SELECT id FROM state_periods"))]
      (let [response (post-json-form (str "/periods/" id "/end") {})]
        (is (some? (and (>= (:status response) 200) (<= (:status response) 299)))
            "закрытие периода возвращает успешный статус"))
      (is (some? (:ended_at (first-row "SELECT ended_at FROM state_periods WHERE id=?" id)))
          "ended_at заполнен после закрытия"))))

(deftest test-entry-during-active-period-linked
  (testing "запись, созданная при активном периоде, автоматически привязывается (state_period_id)"
    (post-json-form "/periods/start" {:label "подъём"})
    (let [pid (:id (first-row "SELECT id FROM state_periods"))]
      (post-entries {:mood_score 7 :energy 8 :anxiety 2 :date "2026-08-20"})
      (let [row (first-row "SELECT state_period_id FROM entries ORDER BY id DESC LIMIT 1")]
        (is (= pid (:state_period_id row)) "новая запись привязана к активному периоду")))))

(deftest test-periods-not-auto-created-after-low-days
  (testing "период не создаётся автоматически даже после 3 дней в плохом состоянии"
    (doseq [n [1 2]]
      (post-entries {:mood_score 2 :energy 2 :anxiety 5 :date (str "2026-08-0" n)}))
    (post-entries {:mood_score 2 :energy 2 :anxiety 5 :date "2026-08-03"})
    (let [rows (jdbc/execute! @ds-atom ["SELECT * FROM state_periods"]
                              {:builder-fn rs/as-unqualified-maps})]
      (is (= 0 (count rows)) "после 3 дней low период не создан автоматически"))))

(deftest test-period-created-for-current-user
  (testing "период создаётся для авторизованного пользователя"
    (post-json-form "/periods/start" {:label "спад"} other-identity)
    (let [row (first-row "SELECT user_id FROM state_periods")]
      (is (= 2 (:user_id row)) "период принадлежит тому, кто его открыл"))))

(deftest test-period-not-closable-by-foreign-user
  (testing "чужой пользователь не может закрыть период другого"
    (post-json-form "/periods/start" {:label "спад"} other-identity)
    (let [id (:id (first-row "SELECT id FROM state_periods"))]
      (post-json-form (str "/periods/" id "/end") {} test-identity)
      (is (nil? (:ended_at (first-row "SELECT ended_at FROM state_periods WHERE id=?" id)))
          "чужой не закрывает чужой период"))))