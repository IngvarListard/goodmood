(ns app.routes.ai-test
  (:require [app.db.ai :as db]
            [app.db.entries :as db.entries]
            [app.domains.ai :as domains]
            [app.middleware :as mw]
            [app.routes.app :as routes]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [jsonista.core :as json]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.session.store :as session.store]))

(defonce ^:private tmp-path "/tmp/goodmood-ai-routes-test.db")

(def ^:private ds-atom (atom nil))

(def ^:private session-secret "ai-routes-test-secret-0123456789abcdef")

(def ^:private cookie-store
  (session.cookie/cookie-store {:key (mw/secret-key session-secret)}))

(def ^:private csrf-token "test-csrf-token")

(defn- today []
  (str (java.time.LocalDate/now)))

(defn- add-today-entry!
  "Создать запись на сегодня (текущее состояние для виджетов AI на /feed)."
  []
  (db.entries/create-entry! @ds-atom
                            {:user-id 1
                             :date (today)
                             :activity ""
                             :effect ""
                             :mood-score 5
                             :energy 4
                             :anxiety 7}))

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

(defn- seed-finding
  [& [uid type]]
  (db/insert-finding! @ds-atom
                      {:user-id (or uid 1)
                       :type (or type "correlation")
                       :content {:title "сон<6ч и тревога" :description "объяснение"}
                       :confidence "high"
                       :source-refs []}))

(deftest test-ai-feedback-hides-finding-without-delete
  (testing "POST /ai/findings/:id/feedback скрывает находку, не удаляя её"
    (let [finding (seed-finding)
          id (:id finding)
          response (post-json-form (str "/ai/findings/" id "/feedback") {:feedback "irrelevant"})]
      (is (= 200 (:status response)) "feedback возвращает 200")
      (let [rows (jdbc/execute! @ds-atom
                                ["SELECT feedback,hidden FROM ai_findings WHERE id=?" id]
                                {:builder-fn rs/as-unqualified-maps})
            row (first rows)]
        (is (= 1 (count rows)) "строка не удаляется жёстко")
        (is (= "irrelevant" (:feedback row)))
        (is (= 1 (:hidden row))))
      (is (= 0 (count (db/get-findings @ds-atom 1 "correlation")))
          "после feedback находка скрыта из выборки"))))

(deftest ai-feedback-rejects-foreign-user
  (testing "чужой пользователь не может дать feedback по чужой находке"
    (let [finding (seed-finding)
          id (:id finding)
          response (post-json-form (str "/ai/findings/" id "/feedback") {:feedback "irrelevant"} other-identity)]
      (is (= 404 (:status response)) "чужая находка -> 404")
      (let [row (first (jdbc/execute! @ds-atom
                                      ["SELECT feedback,hidden FROM ai_findings WHERE id=?" id]
                                      {:builder-fn rs/as-unqualified-maps}))]
        (is (nil? (:feedback row)))
        (is (= 0 (:hidden row)))))))

(deftest ai-ai-routes-require-auth
  (testing "неавторизованные запросы к AI-роутам отклоняются (не публичные)"
    (doseq [uri ["/ai/correlations" "/ai/label" "/ai/advice" "/ai/findings/1/feedback"]]
      (let [response ((app) {:request-method :post :uri uri
                             :headers {"content-type" "application/json" "accept" "text/html"}
                             :body (java.io.ByteArrayInputStream. (.getBytes "{}"))})]
        (is (some? (:status response)) uri)
        (is (not= 200 (:status response)) (str uri " роут не должен быть публичным"))))))

(deftest test-ai-feed-hides-foreign-findings
  (testing "/feed показывает только находки текущего пользователя"
    (db/insert-finding! @ds-atom
                        {:user-id 2 :type "correlation"
                         :content {:title "СОВЕРШЕННО ЧУЖОЙ ПАТТЕРН" :description "x"}
                         :confidence "high"
                         :source-refs []})
    (let [body (body-text (get-html "/feed"))]
      (is (not (str/includes? body "СОВЕРШЕННО ЧУЖОЙ ПАТТЕРН"))
          "чужая находка не рендерится на моём /feed"))))

(deftest ai-settings-update-persists
  (testing "POST /settings/ai сохраняет настройки и возвращает «Сохранено»"
    (let [response (post-json-form "/settings/ai" {:master_enabled "1"
                                                   :correlations_enabled "0"
                                                   :labels_enabled "1"
                                                   :advice_enabled "1"})
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "Сохранено"))
      (let [settings (domains/get-settings @ds-atom 1)]
        (is (= 1 (:master-enabled settings)))
        (is (= 0 (:correlations-enabled settings)))
        (is (= 1 (:advice-enabled settings)))))))

(deftest ai-settings-per-function-disable-correlations-only
  (testing "отключение корреляций не выключает советы из инсайтов"
    (post-json-form "/settings/ai" {:master_enabled "1"
                                    :correlations_enabled "0"
                                    :labels_enabled "1"
                                    :advice_enabled "1"})
    (is (false? (domains/correlations-enabled? @ds-atom 1)))
    (is (true? (domains/advice-enabled? @ds-atom 1)))
    (is (true? (domains/labels-enabled? @ds-atom 1)))))

(deftest ai-settings-master-off-disables-all
  (testing "master OFF скрывает/отключает все AI-функции"
    (post-json-form "/settings/ai" {:master_enabled "0"
                                    :correlations_enabled "1"
                                    :labels_enabled "1"
                                    :advice_enabled "1"})
    (is (false? (domains/ai-enabled? @ds-atom 1)))
    (is (false? (domains/correlations-enabled? @ds-atom 1)))
    (is (false? (domains/advice-enabled? @ds-atom 1)))
    (is (false? (domains/labels-enabled? @ds-atom 1)))))

(deftest ai-settings-page-shows-ai-section
  (testing "/settings рендерит секцию AI с master-toggle"
    (let [body (body-text (get-html "/settings"))]
      (is (str/includes? body "AI"))
      (is (str/includes? body "master")))))

(deftest test-ai-feed-shows-correlations-section
  (testing "/feed показывает секцию ai-correlations с объяснением и уровнем уверенности"
    (db/insert-finding! @ds-atom
                        {:user-id 1 :type "correlation"
                         :content {:title "сон<6ч и тревога" :description "после недосыпа тревога растёт"}
                         :confidence "high"
                         :source-refs []})
    (let [body (body-text (get-html "/feed"))]
      (is (str/includes? body "ai-correlations") "секция корреляций рендерится")
      (is (str/includes? body "после недосыпа тревога растёт") "объяснение паттерна показано")
      (is (str/includes? body "высокая уверенность") "уровень уверенности показан (high -> «высокая уверенность»)"))))

(deftest test-ai-feed-shows-state-label-proposal
  (testing "/feed показывает AI-предложение ai-state-label рядом с ярлыком"
    (db/insert-finding! @ds-atom
                        {:user-id 1 :type "label"
                         :content {:label "тревожный интроверт" :explanation "высокая тревога и низкая энергия"}
                         :confidence "high"
                         :source-refs []})
    (let [body (body-text (get-html "/feed"))]
      (is (str/includes? body "ai-state-label") "секция AI-ярлыка рендерится")
      (is (str/includes? body "тревожный интроверт")))))

(deftest test-ai-feed-shows-advice-widget
  (testing "/feed показывает секцию ai-advice с советом из своих инсайтов (при текущем состоянии)"
    (add-today-entry!)
    (let [iid (:id (jdbc/execute-one! @ds-atom
                                      ["INSERT INTO insights (user_id, context, category, advice_to_self, state_label) VALUES (1,'тревога','coping','[\"дыхание\"]','anxiety') RETURNING id"]
                                      {:builder-fn rs/as-unqualified-maps}))]
      (db/insert-finding! @ds-atom
                          {:user-id 1 :type "advice"
                           :content {:message "сделай дыхание 4-7-8" :explanation "из твоего инсайта о тревоге"}
                           :confidence "high"
                           :source-refs [iid]})
      (let [body (body-text (get-html "/feed"))]
        (is (str/includes? body "ai-advice") "виджет ai-advice рендерится")
        (is (str/includes? body "сделай дыхание 4-7-8") "текст совета показан")))))

(deftest test-ai-feed-master-off-hides-all-sections
  (testing "master OFF скрывает все AI-секции с /feed"
    (add-today-entry!)
    (db/insert-finding! @ds-atom {:user-id 1 :type "correlation"
                                  :content {:title "c"} :confidence "high" :source-refs []})
    (db/insert-finding! @ds-atom {:user-id 1 :type "advice"
                                  :content {:message "a"} :confidence "high" :source-refs []})
    (db/insert-finding! @ds-atom {:user-id 1 :type "label"
                                  :content {:label "l"} :confidence "high" :source-refs []})
    (post-json-form "/settings/ai" {:master_enabled "0"
                                    :correlations_enabled "1"
                                    :labels_enabled "1"
                                    :advice_enabled "1"})
    (let [body (body-text (get-html "/feed"))]
      (is (not (str/includes? body "ai-correlations")))
      (is (not (str/includes? body "ai-advice")))
      (is (not (str/includes? body "ai-state-label"))))))

(deftest test-ai-feed-disables-correlations-only
  (testing "отключение только корреляций оставляет советы на /feed"
    (add-today-entry!)
    (db/insert-finding! @ds-atom {:user-id 1 :type "correlation"
                                  :content {:title "c"} :confidence "high" :source-refs []})
    (db/insert-finding! @ds-atom {:user-id 1 :type "advice"
                                  :content {:message "a"} :confidence "high" :source-refs []})
    (post-json-form "/settings/ai" {:master_enabled "1"
                                    :correlations_enabled "0"
                                    :labels_enabled "1"
                                    :advice_enabled "1"})
    (let [body (body-text (get-html "/feed"))]
      (is (not (str/includes? body "ai-correlations")) "корреляции скрыты")
      (is (str/includes? body "ai-advice") "советы продолжают работать"))))

(defn- create-state-label-finding!
  "Создать AI-предложение ярлыка для текущего пользователя (user-id 1)."
  [& [label]]
  (db/insert-finding! @ds-atom
                      {:user-id 1 :type "label"
                       :content {:label (or label "тревожный интроверт") :explanation "высокая тревога"}
                       :confidence "high"
                       :source-refs []}))

(deftest test-ai-label-apply-sets-manual-label-and-returns-widget
  (testing "POST /ai/label/apply с label сохраняет ручной ярлык и возвращает виджет с выбранным текстом"
    (add-today-entry!)
    (create-state-label-finding!)
    (let [response (post-json-form "/ai/label/apply" {:label "elevated"})
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/includes? body "подъём") "виджет показывает локализованный выбранный ярлык")
      (let [row (first (jdbc/execute! @ds-atom
                                      ["SELECT state_label FROM entries ORDER BY id DESC LIMIT 1"]
                                      {:builder-fn rs/as-unqualified-kebab-maps}))]
        (is (= "state/elevated" (:state-label row)) "ярлык записан в последнюю запись")))))

(deftest test-ai-label-apply-reject-hides-proposal
  (testing "POST /ai/label/apply с reject=true скрывает AI-предложение (пустой фрагмент)"
    (create-state-label-finding!)
    (let [response (post-json-form "/ai/label/apply" {:reject "true"})
          body (body-text response)]
      (is (= 200 (:status response)))
      (is (str/blank? body) "при отклонении виджет заменяется пустым")
      (is (= [] (db/get-findings @ds-atom 1 "label"))))))

(deftest test-ai-label-apply-requires-auth
  (testing "неавторизованный запрос к /ai/label/apply отклоняется"
    (let [response ((app) {:request-method :post :uri "/ai/label/apply"
                           :headers {"content-type" "application/json" "accept" "text/html"}
                           :body (java.io.ByteArrayInputStream. (.getBytes "{}"))})]
      (is (some? (:status response)))
      (is (not= 200 (:status response))))))