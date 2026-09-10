(ns app.routes.medications-test
  (:require [app.middleware :as mw]
            [app.routes.app :as routes]
            [clojure.string :as str]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [cheshire.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.session.store :as session.store]))

(def ^:private ds-atom (atom nil))

(def ^:private session-secret "med-routes-test-secret-0123456789abcdef")

(def ^:private cookie-store
  (session.cookie/cookie-store {:key (mw/secret-key session-secret)}))

(def ^:private csrf-token "test-csrf-token")

(def ^:private test-identity
  {:id 1 :email "user@test.dev" :role "user" :display-name "Test User"})

(use-fixtures :each #(test-helpers/with-test-db :med-routes ds-atom %))

(defn- app []
  (routes/->app @ds-atom session-secret))

(defn- with-session
  [request session]
  (let [sealed (session.store/write-session cookie-store nil session)]
    (assoc request :cookies {"gm-session" {:value sealed}})))

(defn- authed
  [request]
  (with-session request {:identity test-identity
                         :ring.middleware.anti-forgery/anti-forgery-token csrf-token}))

(defn- with-csrf-header
  [request]
  (assoc-in request [:headers "x-csrf-token"] csrf-token))

(defn- json-body
  [body]
  (json/generate-string body))

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
  [uri]
  ((app)
   (authed {:request-method :get
            :uri uri
            :headers {"accept" "text/html"}
            :body nil})))

(defn- post-json
  "POST с JSON-телом (как json-enc от htmx: значения — строки, CSRF в теле)."
  [uri payload]
  ((app)
   (-> {:request-method :post
        :uri uri
        :headers {"content-type" "application/json"
                  "accept" "application/json"
                  "hx-request" "true"}
        :body (java.io.ByteArrayInputStream.
               (.getBytes (json-body (assoc payload "__anti-forgery-token" csrf-token))))}
       authed)))

(defn- post-plain
  "POST без тела (deactivate/activate) с CSRF-заголовком."
  [uri]
  ((app)
   (-> {:request-method :post
        :uri uri
        :headers {"accept" "text/html" "hx-request" "true"}
        :body nil}
       authed
       with-csrf-header)))

(defn- create-med!
  "Создать медикамент через API и вернуть его id."
  []
  (let [response (post-json "/medications" {:name "Препарат А"
                                            :dose "600"
                                            :dose_unit "мг"
                                            :schedule "08:00, 20:00"
                                            :sensitive "1"
                                            :notes "n"})]
    (is (= 201 (:status response)) "creating a medication succeeds")
    (some-> (second (re-find #"id=\"med-(\d+)\"" (body-text response)))
            parse-long)))

(defn- med-count
  []
  (count (jdbc/execute! @ds-atom ["SELECT * FROM medications"])))

(defn- log-count
  []
  (count (jdbc/execute! @ds-atom ["SELECT * FROM medication_logs"])))

(deftest medications-page-returns-200
  (testing "GET /medications returns the page with title and empty state"
    (let [response (get-html "/medications")
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? (content-type response) "text/html"))
      (is (str/starts-with? body "<html"))
      (is (str/includes? body ">Медикаменты<"))
      (is (str/includes? body "Здесь будут ваши медикаменты"))
      (is (str/includes? body "id=\"med-modal\"")
          "page includes the modal dialog"))))

(deftest new-modal-returns-form
  (testing "GET /medications/new returns a form with a CSRF token"
    (let [response (get-html "/medications/new")
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "Новый медикамент"))
      (is (str/includes? body "name=\"__anti-forgery-token\"")))))

(deftest post-medication-creates-register
  (testing "POST /medications creates a medication and returns updated fragment"
    (let [response (post-json "/medications" {:name "Препарат А"
                                              :dose "600"
                                              :dose_unit "мг"
                                              :schedule "08:00, 20:00"})]
      (is (= 201 (:status response)))
      (is (str/includes? (content-type response) "text/html"))
      (let [body (body-text response)]
        (is (str/includes? body "id=\"med-content\""))
        (is (str/includes? body ">Препарат А<"))
        (is (str/includes? body "600 мг · 08:00, 20:00"))
        (is (str/includes? body "id=\"intake-widget\"")))
      (is (= 1 (med-count))))))

(deftest post-medication-validation-error
  (testing "POST /medications without name returns 400 HTML alert-warning fragment"
    (let [response (post-json "/medications" {:dose "600"
                                              :dose_unit "мг"
                                              :schedule "08:00"})]
      (is (= 400 (:status response)))
      (let [body (body-text response)]
        (is (str/includes? body "alert alert-warning"))
        (is (not (str/includes? body "alert alert-error")))
        (is (str/includes? body "name")))
      (is (= 0 (med-count))))))

(deftest post-medication-log-201
  (testing "POST /medications/:id/log with status=taken returns the updated slot"
    (let [med-id (create-med!)]
      (let [response (post-json (str "/medications/" med-id "/log")
                                {:medication_id (str med-id)
                                 :scheduled_time "08:00"
                                 :status "taken"})]
        (is (= 201 (:status response)))
        (is (str/includes? (content-type response) "text/html"))
        (let [body (body-text response)]
          (is (str/includes? body "badge-success"))
          (is (str/includes? body ">принял<"))
          (is (str/includes? body ">Отменить<")))
        (is (= 1 (log-count)))))))

(deftest log-repeats-not-duplicate
  (testing "repeated POST /medications/:id/log updates the log, not duplicates"
    (let [med-id (create-med!)]
      (post-json (str "/medications/" med-id "/log")
                 {:medication_id (str med-id)
                  :scheduled_time "08:00"
                  :status "taken"})
      (post-json (str "/medications/" med-id "/log")
                 {:medication_id (str med-id)
                  :scheduled_time "08:00"
                  :status "skipped"})
      (is (= 1 (log-count)))
      (is (= 0 (count (jdbc/execute! @ds-atom
                                     ["SELECT * FROM medication_logs WHERE status='taken'"])))))))

(deftest log-cancel-deletes-slot
  (let [med-id (create-med!)]
    (post-json (str "/medications/" med-id "/log")
               {:medication_id (str med-id)
                :scheduled_time "08:00"
                :status "taken"})
    (let [response (post-json (str "/medications/" med-id "/log")
                              {:medication_id (str med-id)
                               :scheduled_time "08:00"
                               :status "pending"})]
      (is (= 201 (:status response)))
      (is (not (str/includes? (body-text response) "badge-success")))
      (is (= 0 (count (jdbc/execute! @ds-atom
                                     ["SELECT * FROM medication_logs WHERE status='taken'"])))))))

(deftest post-deactivate-and-activate
  (testing "POST deactivate returns fragment with inactive section, activate brings it back"
    (let [med-id (create-med!)]
      (let [deactivated (post-plain (str "/medications/" med-id "/deactivate"))]
        (is (= 200 (:status deactivated)))
        (let [body (body-text deactivated)]
          (is (str/includes? body "Неактивные"))
          (is (str/includes? body "Активировать"))))
      (let [activated (post-plain (str "/medications/" med-id "/activate"))]
        (is (= 200 (:status activated)))
        (is (not (str/includes? (body-text activated) "Неактивные")))))))

(deftest medications-scoped-per-user
  (testing "another user cannot deactivate someone else's medication (404)"
    (let [med-id (create-med!)
          response ((app)
                    (-> {:request-method :post
                         :uri (str "/medications/" med-id "/deactivate")
                         :headers {"accept" "text/html"}
                         :body nil}
                        (with-session {:identity {:id 2 :email "other@test.dev" :role "user"
                                                  :display-name "Other"}
                                       :ring.middleware.anti-forgery/anti-forgery-token csrf-token})
                        with-csrf-header))]
      (is (= 404 (:status response)))
      (is (= 1 (:active (jdbc/execute-one! @ds-atom
                                           ["SELECT active FROM medications"]
                                           {:builder-fn rs/as-unqualified-kebab-maps})))))))