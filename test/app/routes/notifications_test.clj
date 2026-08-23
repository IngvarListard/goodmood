(ns app.routes.notifications-test
  (:require [app.middleware :as mw]
            [app.routes.app :as routes]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [jsonista.core :as json]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.session.store :as session.store]))

(defonce ^:private tmp-path "/tmp/goodmood-notif-routes-test.db")

(def ^:private ds-atom (atom nil))

(def ^:private session-secret "notif-routes-test-secret-0123456789abcdef")

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

(defn- with-session [request session]
  (let [sealed (session.store/write-session cookie-store nil session)]
    (assoc request :cookies {"gm-session" {:value sealed}})))

(defn- authed
  [request identity]
  (with-session request {:identity identity
                         :ring.middleware.anti-forgery/anti-forgery-token csrf-token}))

(defn- with-csrf-header
  [request]
  (assoc-in request [:headers "x-csrf-token"] csrf-token))

(defn- body-text
  [response]
  (let [body (:body response)]
    (if (instance? java.io.InputStream body)
      (slurp body)
      (str body))))

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

(defn- post-json-form
  "POST с JSON-телом и заголовком hx-request — как htmx json-enc."
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

(deftest settings-shows-notification-section
  (testing "/settings рендерит секцию уведомлений с тремя слотами"
    (let [body (body-text (get-html "/settings"))]
      (is (str/includes? body ">Уведомления<"))
      (is (str/includes? body "name=\"time_morning\""))
      (is (str/includes? body "name=\"time_midday\""))
      (is (str/includes? body "name=\"time_evening\""))
      (is (str/includes? body "name=\"enabled_morning\"") "toggle morning"))))

(deftest settings-update-persists-slots
  (testing "POST /settings/notifications сохраняет и рендерит «Сохранено»"
    (let [response
          (post-json-form
           "/settings/notifications"
           {:enabled_morning "on" :time_morning "07:00"
            :enabled_midday nil :time_midday "12:00"
            :enabled_evening "on" :time_evening "18:00"})
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "Сохранено"))
      (let [rows (jdbc/execute! @ds-atom
                                ["SELECT slot, enabled, time FROM user_notification_settings WHERE user_id = ?" 1]
                                {:builder-fn next.jdbc.result-set/as-unqualified-maps})
            by-slot (into {} (map (juxt :slot identity) rows))]
        (is (= "07:00" (:time (get by-slot "morning"))))
        (is (= 0 (:enabled (get by-slot "midday"))))
        (is (= 1 (:enabled (get by-slot "evening"))))))))

(deftest summary-dismiss-sets-sentinel
  (testing "POST /notifications/summary-dismiss ставит last_summary_date = сегодня"
    (post-json-form "/notifications/summary-dismiss" {})
    (let [rows (jdbc/execute! @ds-atom
                              ["SELECT last_summary_date FROM user_notification_settings WHERE user_id = 1 AND slot = 'evening'"]
                              {:builder-fn next.jdbc.result-set/as-unqualified-maps})
          date (:last_summary_date (first rows))]
      (is (= (str (java.time.LocalDate/now)) date)))))