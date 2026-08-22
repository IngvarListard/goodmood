(ns app.routes.auth-test
  (:require [app.db.entries :as db]
            [app.domains.users :as users]
            [app.middleware :as mw]
            [app.routes.app :as routes]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.session.store :as session.store]))

(defonce ^:private tmp-path "/tmp/goodmood-auth-test.db")

(def ^:private ds-atom (atom nil))

(def ^:private session-secret "auth-test-secret-0123456789abcdef")

(def ^:private cookie-store
  (session.cookie/cookie-store {:key (mw/secret-key session-secret)}))

(def ^:private csrf-token "test-csrf-token")

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

(defn- with-csrf-session
  "Session containing only the CSRF token (unauthenticated)."
  [request]
  (with-session request {:ring.middleware.anti-forgery/anti-forgery-token csrf-token}))

(defn- authed
  "Authenticated session with identity and CSRF token."
  [request identity]
  (with-session request {:identity identity
                         :ring.middleware.anti-forgery/anti-forgery-token csrf-token}))

(defn- body-text
  [response]
  (let [body (:body response)]
    (if (instance? java.io.InputStream body)
      (slurp body)
      (str body))))

(defn- location
  [response]
  (get-in response [:headers "Location"]))

(defn- session-cookie-value
  "Extract the gm-session cookie value from the response Set-Cookie header
   (form-decoded back to the raw sealed value)."
  [response]
  (when-let [set-cookie (get-in response [:headers "Set-Cookie"])]
    (let [set-cookie (if (coll? set-cookie) (first set-cookie) set-cookie)]
      (some-> (re-find #"(?:^|, )gm-session=([^;]*)" set-cookie)
              second
              ring.util.codec/form-decode))))

(defn- login-post
  [request email password]
  ((app)
   (-> (assoc request :form-params {"email" email
                                    "password" password
                                    "__anti-forgery-token" csrf-token})
       with-csrf-session)))

(defn- create-user!
  [email password]
  (users/create-user @ds-atom {:email email
                               :password password
                               :display-name "Test User"}))

(deftest protected-route-redirects-unauthenticated
  (testing "GET /feed without session redirects to /login?next=/feed"
    (let [response ((app) {:request-method :get
                           :uri "/feed"
                           :headers {"accept" "text/html"}
                           :body nil})]
      (is (= 302 (:status response)))
      (is (= "/login?next=/feed" (location response))))))

(deftest api-route-redirects-unauthenticated
  (testing "GET /entries without session redirects to /login"
    (let [response ((app) {:request-method :get
                           :uri "/entries"
                           :headers {"accept" "application/json"}
                           :body nil})]
      (is (= 302 (:status response)))
      (is (= "/login?next=/entries" (location response))))))

(deftest nonexistent-route-redirects-unauthenticated
  (testing "GET /nonexistent without session redirects to /login (no access under any pretext)"
    (let [response ((app) {:request-method :get
                           :uri "/nonexistent"
                           :body nil})]
      (is (= 302 (:status response)))
      (is (= "/login?next=/nonexistent" (location response))))))

(deftest health-check-and-login-are-public
  (testing "GET / and GET /login are accessible without a session"
    (let [health ((app) {:request-method :get :uri "/" :body nil})
          login ((app) {:request-method :get
                        :uri "/login"
                        :headers {"accept" "text/html"}
                        :body nil})]
      (is (= 200 (:status health)))
      (is (= "OK" (body-text health)))
      (is (= 200 (:status login))))))

(deftest failed-login-shows-error
  (testing "POST /login with wrong password returns 200 with error message"
    (create-user! "a@b.c" "correct-horse")
    (let [response (login-post {:request-method :post :uri "/login"} "a@b.c" "wrong-pass")]
      (is (= 200 (:status response)))
      (is (str/includes? (body-text response) "Неверный email или пароль"))
      (is (not (str/includes? (body-text response) "correct-horse"))
          "error page must not contain the password"))))

(deftest failed-login-for-unknown-email
  (testing "POST /login with unknown email returns 200 with error message"
    (let [response (login-post {:request-method :post :uri "/login"} "nobody@b.c" "whatever")]
      (is (= 200 (:status response)))
      (is (str/includes? (body-text response) "Неверный email или пароль")))))

(deftest successful-login-sets-session
  (testing "POST /login with valid credentials sets a session cookie and redirects"
    (create-user! "a@b.c" "correct-horse")
    (let [response (login-post {:request-method :post :uri "/login"} "a@b.c" "correct-horse")]
      (is (= 302 (:status response)))
      (is (= "/" (location response)))
      (is (some? (session-cookie-value response))
          "session cookie must be set")
      (let [follow-up ((app) (assoc {:request-method :get
                                     :uri "/feed"
                                     :headers {"accept" "text/html"}
                                     :body nil}
                                    :cookies {"gm-session"
                                              {:value (session-cookie-value response)}}))]
        (is (= 200 (:status follow-up))
            "logged-in session accesses protected pages")))))

(deftest successful-login-redirects-to-next
  (testing "POST /login with valid credentials and next param redirects to /feed"
    (create-user! "a@b.c" "correct-horse")
    (let [response ((app)
                    (-> {:request-method :post
                         :uri "/login"
                         :form-params {"email" "a@b.c"
                                       "password" "correct-horse"
                                       "next" "/feed"
                                       "__anti-forgery-token" csrf-token}}
                        with-csrf-session))]
      (is (= 302 (:status response)))
      (is (= "/feed" (location response))))))

(deftest login-page-redirects-authenticated
  (testing "GET /login with valid session redirects to /"
    (let [response ((app) (authed {:request-method :get
                                   :uri "/login"
                                   :headers {"accept" "text/html"}
                                   :body nil}
                                  {:id 1 :email "a@b.c" :role "user" :display-name "A"}))]
      (is (= 302 (:status response)))
      (is (= "/" (location response))))))

(deftest logout-clears-session
  (testing "POST /logout invalidates the session and redirects to /login"
    (let [auth-cookie (session-cookie-value
                       ((app) (authed {:request-method :get
                                       :uri "/feed"
                                       :headers {"accept" "text/html"}
                                       :body nil}
                                      {:id 1 :email "a@b.c" :role "user" :display-name "A"})))
          response ((app)
                    (-> {:request-method :post
                         :uri "/logout"
                         :form-params {"__anti-forgery-token" csrf-token}}
                        (with-session {:identity {:id 1 :email "a@b.c" :role "user" :display-name "A"}
                                       :ring.middleware.anti-forgery/anti-forgery-token csrf-token})))]
      (is (= 302 (:status response)))
      (is (= "/login" (location response)))
      (let [cookie-after (session-cookie-value response)]
        (is (not= auth-cookie cookie-after)
            "session cookie must change after logout")
        (let [follow-up ((app)
                         (-> {:request-method :get
                              :uri "/feed"
                              :headers {"accept" "text/html"}
                              :body nil}
                             (assoc :cookies {"gm-session" {:value cookie-after}})))]
          (is (= 302 (:status follow-up))
              "logged-out session must not access protected pages"))))))

(deftest csrf-blocks-tokenless-post
  (testing "POST /login without a CSRF token is rejected with 403"
    (let [response ((app) {:request-method :post
                           :uri "/login"
                           :form-params {"email" "a@b.c" "password" "x"}})]
      (is (= 403 (:status response))))))

(deftest authenticated-user-gets-protected-pages
  (testing "GET /feed with session returns 200 and user chip"
    (let [response ((app) (authed {:request-method :get
                                   :uri "/feed"
                                   :headers {"accept" "text/html"}
                                   :body nil}
                                  {:id 1 :email "a@b.c" :role "user" :display-name "Alice"}))]
      (is (= 200 (:status response)))
      (let [html (body-text response)]
        (is (str/includes? html "<html"))
        (is (str/includes? html "Alice"))
        (is (str/includes? html "Выйти"))))))

(deftest entries-scoped-per-user
  (testing "users only see their own entries"
    (let [user1 (create-user! "one@b.c" "password1")
          user2 (create-user! "two@b.c" "password2")
          post1 ((app) (-> {:request-method :post
                            :uri "/entries"
                            :headers {"content-type" "application/json"
                                      "accept" "application/json"
                                      "x-csrf-token" csrf-token}
                            :body (java.io.ByteArrayInputStream.
                                   (.getBytes "{\"mood_score\":7,\"energy\":8,\"anxiety\":2}"))}
                           (with-session {:identity {:id (:id user1)
                                                     :email "one@b.c"
                                                     :role "user"
                                                     :display-name "One"}
                                          :ring.middleware.anti-forgery/anti-forgery-token csrf-token})))
          post2 ((app) (-> {:request-method :post
                            :uri "/entries"
                            :headers {"content-type" "application/json"
                                      "accept" "application/json"
                                      "x-csrf-token" csrf-token}
                            :body (java.io.ByteArrayInputStream.
                                   (.getBytes "{\"mood_score\":8,\"energy\":3,\"anxiety\":7}"))}
                           (with-session {:identity {:id (:id user2)
                                                     :email "two@b.c"
                                                     :role "user"
                                                     :display-name "Two"}
                                          :ring.middleware.anti-forgery/anti-forgery-token csrf-token})))]
      (is (= 201 (:status post1)))
      (is (= 201 (:status post2)))
      (let [rows (jdbc/execute! @ds-atom ["SELECT mood_score FROM entries WHERE user_id = ?" (:id user1)])]
        (is (= 1 (count rows)))
        (is (= 7 (:entries/mood_score (first rows)))))
      (let [row (first (jdbc/execute! @ds-atom ["SELECT mood_score FROM entries WHERE user_id = ?" (:id user2)]))]
        (is (= 8 (:entries/mood_score row)))))
    (is (= 2 (count (jdbc/execute! @ds-atom ["SELECT * FROM entries"]))))))

(deftest locale-cookie-picks-english
  (testing "gm-locale=en cookie renders English UI with lang=\"en\""
    (let [response ((app) {:request-method :get
                           :uri "/login"
                           :headers {"accept" "text/html"
                                     "cookie" "gm-locale=en"}
                           :body nil})
          html (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? html "lang=\"en\""))
      (is (str/includes? html ">Log in<")))))

(deftest accept-language-fallback-to-english
  (testing "without cookie, Accept-Language en-US selects English"
    (let [response ((app) {:request-method :get
                           :uri "/login"
                           :headers {"accept" "text/html"
                                     "accept-language" "en-US,en;q=0.9"}
                           :body nil})
          html (body-text response)]
      (is (str/includes? html "lang=\"en\""))
      (is (str/includes? html ">Log in<")))))

(deftest default-locale-russian
  (testing "without cookie and unsupported Accept-Language defaults to Russian"
    (let [response ((app) {:request-method :get
                           :uri "/login"
                           :headers {"accept" "text/html"
                                     "accept-language" "de-DE"}
                           :body nil})
          html (body-text response)]
      (is (str/includes? html "lang=\"ru\""))
      (is (str/includes? html ">Войти<")))))

(deftest locale-switch-sets-cookie
  (testing "POST /locale sets the gm-locale cookie and redirects"
    (let [response ((app)
                    (-> {:request-method :post
                         :uri "/locale"
                         :form-params {"locale" "en" "next" "/settings"
                                       "__anti-forgery-token" csrf-token}}
                        (with-session {:ring.middleware.anti-forgery/anti-forgery-token csrf-token})))]
      (is (= 302 (:status response)))
      (is (= "/settings" (location response)))
      (let [set-cookie (get-in response [:headers "Set-Cookie"])
            cookies (if (coll? set-cookie) set-cookie [set-cookie])]
        (is (some #(str/includes? % "gm-locale=en") cookies)
            "gm-locale cookie must be set to en")))))

(deftest seed-admin-creates-once
  (testing "seed-admin! creates an admin on empty DB and assigns orphan entries"
    (db/create-entry! @ds-atom {:date "2026-08-04"
                                :activity "orphan"
                                :effect "e"
                                :mood-score 5})
    (let [admin (users/seed-admin! @ds-atom {"GOODMOOD_ADMIN_EMAIL" "admin@test.local"
                                             "GOODMOOD_ADMIN_PASSWORD" "admin-pass-123"})]
      (is (some? admin))
      (is (= "admin" (:role admin)))
      (is (= "admin@test.local" (:email admin)))
      (let [entries (db/get-entries @ds-atom (:id admin))]
        (is (= 1 (count entries)) "orphan entries must be assigned to admin"))
      (is (nil? (users/seed-admin! @ds-atom {"GOODMOOD_ADMIN_PASSWORD" "x"}))
          "second call does not create duplicates"))))

(deftest seed-admin-requires-password-env
  (testing "seed-admin! throws when GOODMOOD_ADMIN_PASSWORD is missing"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"GOODMOOD_ADMIN_PASSWORD"
                          (users/seed-admin! @ds-atom {"GOODMOOD_ADMIN_EMAIL" "a@b.c"})))))