(ns app.routes-test
  (:require [app.domains.entries.db :as db]
            [app.routes :as routes]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [jsonista.core :as json]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]))

(defonce ^:private tmp-path "/tmp/goodmood-routes-test.db")

(def ^:private ds-atom (atom nil))

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
  (routes/->app @ds-atom))

(defn- json-body
  [body]
  (json/write-value-as-string body))

(defn- post-entries
  [payload]
  ((app)
   {:request-method :post
    :uri "/entries"
    :headers {"content-type" "application/json" "accept" "application/json"}
    :body (java.io.ByteArrayInputStream. (.getBytes (json-body payload)))}))

(defn- get-entries
  []
  ((app)
   {:request-method :get
    :uri "/entries"
    :headers {"accept" "application/json"}
    :body nil}))

(def ^:private keyword-keys
  (json/object-mapper {:decode-key-fn true}))

(defn- response-body
  [response]
  (json/read-value (:body response) keyword-keys))

(deftest create-entry-returns-201
  (testing "POST /entries with valid body returns 201 and created entry"
    (let [response (post-entries {:activity "walk"
                                  :effect "calm"
                                  :mood_score 7
                                  :sleep_hours 8.0})]
      (is (= 201 (:status response)))
      (let [entry (response-body response)]
        (is (= "walk" (:activity entry)))
        (is (= "calm" (:effect entry)))
        (is (= 7 (:mood-score entry)))
        (is (= 8.0 (:sleep-hours entry)))
        (is (pos? (:id entry)))
        (is (not (nil? (:created-at entry))))))))

(deftest create-entry-rejects-out-of-range-mood
  (testing "POST /entries with mood_score 15 returns 400 with validation error"
    (let [response (post-entries {:activity "walk"
                                  :effect "calm"
                                  :mood_score 15})]
      (is (= 400 (:status response)))
      (is (some? (get-in (response-body response) [:errors :mood_score]))))))

(deftest create-entry-rejects-missing-field
  (testing "POST /entries without required field returns 400 with validation error"
    (let [response (post-entries {:activity "walk"
                                  :mood_score 5})]
      (is (= 400 (:status response)))
      (is (some? (get-in (response-body response) [:errors :effect]))))))

(deftest get-entries-returns-200
  (testing "GET /entries returns 200 with stored entries"
    (post-entries {:activity "walk" :effect "calm" :mood_score 7 :sleep_hours 8.0})
    (post-entries {:activity "run" :effect "energetic" :mood_score 6 :sleep_hours 7.5})
    (let [response (get-entries)]
      (is (= 200 (:status response)))
      (let [entries (response-body response)]
        (is (= 2 (count entries)))
        (is (= #{"walk" "run"} (set (map :activity entries))))))))

(deftest get-entries-within-time-limit
  (testing "GET /entries with up to 1000 entries responds within 200ms"
    (dotimes [i 1000]
      (db/create-entry! @ds-atom {:date "2026-08-04"
                                  :activity (str "a" i)
                                  :effect "e"
                                  :mood-score 5}))
    (let [handler (app)
          start (System/nanoTime)
          response (handler {:request-method :get
                             :uri "/entries"
                             :headers {"accept" "application/json"}
                             :body nil})
          elapsed-ms (/ (- (System/nanoTime) start) 1e6)]
      (is (= 200 (:status response)))
      (is (< elapsed-ms 200) (str "GET /entries took " elapsed-ms "ms")))))
