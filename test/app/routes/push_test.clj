(ns app.routes.push-test
  (:require [app.domains.push :as push-domains]
            [app.middleware :as mw]
            [app.routes.app :as routes]
            [app.test-helpers :as test-helpers]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [cheshire.core :as json]
            [next.jdbc :as jdbc]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.session.store :as session.store]))

(def ^:private ds-atom (atom nil))

(def ^:private session-secret "push-routes-test-secret-0123456789abcdef")

(def ^:private cookie-store
  (session.cookie/cookie-store {:key (mw/secret-key session-secret)}))

(def ^:private csrf-token "test-csrf-token")

(def ^:private test-identity
  {:id 1 :email "user@test.dev" :role "user" :display-name "Test User"})

(use-fixtures :each #(test-helpers/with-test-db :push-routes ds-atom %))

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
               (.getBytes (json/generate-string
                           (assoc payload "__anti-forgery-token" csrf-token))))}
       (authed (or identity test-identity)))))

(deftest subscribe-persists-and-renders-enabled
  (testing "POST /push/subscribe сохраняет подписку и рендерит включённое состояние"
    (with-redefs [push-domains/vapid-configured? (constantly true)]
      (let [response
            (post-json-form
             "/push/subscribe"
             {:endpoint "https://fcm.example.com/fcm/send/ep-1"
              :keys {:p256dh "p256dh-key" :auth "auth-key"}
              :expirationTime nil})
            body (body-text response)]
        (is (= 200 (:status response)))
        (is (str/includes? body "id=\"push-section\""))
        (is (str/includes? body "Уведомления включены"))
        (let [rows (jdbc/execute! @ds-atom
                                  ["SELECT endpoint, p256dh, auth FROM push_subscriptions WHERE user_id = ?" 1]
                                  {:builder-fn next.jdbc.result-set/as-unqualified-maps})]
          (is (= 1 (count rows)))
          (is (= "https://fcm.example.com/fcm/send/ep-1" (:endpoint (first rows))))
          (is (= "p256dh-key" (:p256dh (first rows))))
          (is (= "auth-key" (:auth (first rows)))))))))

(deftest test-send-renders-delivery-count
  (testing "POST /push/test рендерит число доставок"
    (with-redefs [push-domains/vapid-configured? (constantly true)
                  push-domains/send-push! (fn [_ds _uid _title _body] 3)]
      (let [response (post-json-form "/push/test" {})
            body (body-text response)]
        (is (= 200 (:status response)))
        (is (str/includes? body "Отправлено: 3"))))))
