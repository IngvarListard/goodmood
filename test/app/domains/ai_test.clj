(ns app.domains.ai-test
  (:require [app.db.ai :as db]
            [app.db.entries :as db.entries]
            [app.db.insights :as db.insights]
            [app.domains.ai :as domains]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def user-id 1)

(defonce ^:private tmp-path "/tmp/goodmood-ai-domain-test.db")

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

(defn- add-entries
  "Создать n записей с чередующимися сном/энергией/тревогой, чтобы AI мог найти корреляцию."
  [n & [uid]]
  (let [uid (or uid user-id)]
    (dotimes [i n]
      (db.entries/create-entry! @ds-atom
                                {:user-id uid
                                 :date (str "2026-07-" (inc i))
                                 :activity "walk"
                                 :effect "calm"
                                 :mood-score 5
                                 :energy (if (even? i) 8 4)
                                 :anxiety (if (even? i) 2 7)
                                 :sleep-hours (if (even? i) 8.0 5.0)}))))

(defn- add-insight
  [uid context state-label]
  (db.insights/create-insight! @ds-atom
                               {:user-id uid
                                :context context
                                :category "coping"
                                :advice-to-self ["дыхание 4-7-8"]
                                :state-label state-label}))

(deftest test-correlation-discovery-ai-finds-sleep-mood-correlation
  (testing "при >=14 днях и значимой корреляции AI возвращает находку с объяснением и confidence"
    (add-entries 20)
    (with-redefs [domains/call-chat
                  (fn [model messages]
                    "[{\"title\":\"сон<6ч и тревога\",\"description\":\"после недосыпа тревога растёт\",\"confidence\":\"high\"}]")]
      (let [findings (domains/analyze-correlations @ds-atom user-id)]
        (is (= 1 (count findings)) "найдена одна корреляция")
        (let [finding (first findings)]
          (is (= "correlation" (:type finding)))
          (is (= "high" (:confidence finding)))
          (is (= "сон<6ч и тревога" (get-in finding [:content :title])))
          (is (some? (get-in finding [:content :description]))
              "есть объяснение паттерна"))))))

(deftest test-correlation-requires-enough-data
  (testing "при <14 дней AI не запускает анализ и находок нет"
    (add-entries 10)
    (let [called (atom 0)]
      (with-redefs [domains/call-chat
                    (fn [model messages] (swap! called inc)
                      "[{\"title\":\"x\",\"description\":\"y\",\"confidence\":\"high\"}]")]
        (is (= [] (domains/analyze-correlations @ds-atom user-id)))
        (is (zero? @called) "call-chat не вызывается при недостатке данных")))))

(deftest test-ai-proposes-state-label-when-no-manual-label
  (testing "без ручного ярлыка AI предлагает текст и объяснение"
    (add-entries 20)
    (with-redefs [domains/call-chat
                  (fn [model messages]
                    "{\"label\":\"тревожный интроверт\",\"explanation\":\"высокая тревога и низкая энергия\",\"confidence\":\"high\"}")]
      (let [proposal (domains/propose-state-label @ds-atom user-id nil)]
        (is (some? proposal))
        (is (= "label" (:type proposal)))
        (is (= "тревожный интроверт" (get-in proposal [:content :label])))
        (is (some? (get-in proposal [:content :explanation])))))))

(deftest test-ai-propose-label-respects-manual-label
  (testing "при ручном ярлыке AI не предлагает (последнее слово за пользователем)"
    (with-redefs [domains/call-chat
                  (fn [model messages] "{\"label\":\"ярлык\",\"explanation\":\"x\"}")]
      (is (nil? (domains/propose-state-label @ds-atom user-id "state/mixed"))))))

(deftest test-ai-advice-from-own-insights
  (testing "совет генерируется из своих инсайтов со ссылками на исходные"
    (let [i1 (add-insight user-id "когда тревога высокая" "anxiety")
          i2 (add-insight user-id "когда энергия низкая" "low")]
      (with-redefs [domains/call-chat
                    (fn [model messages]
                      (str "{\"advice\":\"сделай дыхание\",\"explanation\":\"из твоих инсайтов\","
                           "\"confidence\":\"high\",\"source_refs\":[" (:id i1) "," (:id i2) "]}"))]
        (let [advice (domains/generate-advice-from-insights @ds-atom user-id)]
          (is (some? advice))
          (let [item (first advice)]
            (is (= "advice" (:type item)))
            (is (= "сделай дыхание" (get-in item [:content :message])))
            (is (= [(:id i1) (:id i2)] (:source-refs item)))))))))

(deftest test-no-own-insights-no-advice
  (testing "без своих инсайтов совет не генерируется и модель не вызывается"
    (let [called (atom 0)]
      (with-redefs [domains/call-chat
                    (fn [model messages] (swap! called inc) "{\"advice\":\"x\"}")]
        (is (= [] (domains/generate-advice-from-insights @ds-atom user-id)))
        (is (zero? @called))))))

(deftest test-ai-uses-only-own-data
  (testing "совет использует только свои инсайты, чужие не участвуют"
    (let [mine (add-insight user-id "anxiety" "anxiety")
          theirs (add-insight 999 "anxiety" "anxiety")]
      (with-redefs [domains/call-chat
                    (fn [model messages]
                      (str "{\"advice\":\"свой совет\",\"explanation\":\"e\",\"confidence\":\"high\","
                           "\"source_refs\":[" (:id mine) "]}"))]
        (let [advice (domains/generate-advice-from-insights @ds-atom user-id)]
          (is (some? advice))
          (let [item (first advice)]
            (is (= [(:id mine)] (:source-refs item)))
            (is (not (contains? (:source-refs item) (:id theirs))))))))))

(deftest test-give-feedback-sets-hidden-and-feedback
  (let [finding (db/insert-finding! @ds-atom
                                    {:user-id user-id
                                     :type "correlation"
                                     :content {:title "нерелевантно"}
                                     :confidence "high"
                                     :source-refs []})
        id (:id finding)]
    (domains/give-feedback @ds-atom user-id id "irrelevant")
    (is (= [] (db/get-findings @ds-atom user-id "correlation")))
    (let [row (first (jdbc/execute! @ds-atom
                                    ["SELECT feedback,hidden FROM ai_findings WHERE id=?" id]
                                    {:builder-fn rs/as-unqualified-maps}))]
      (is (= "irrelevant" (:feedback row)))
      (is (= 1 (:hidden row))))))

(deftest test-ai-settings-per-function-opt-out
  (testing "per-function отключение не трогает остальные функции"
    (domains/update-settings @ds-atom user-id
                             {:master_enabled 1 :correlations_enabled 0
                              :labels_enabled 1 :advice_enabled 1})
    (is (false? (domains/correlations-enabled? @ds-atom user-id)))
    (is (true? (domains/advice-enabled? @ds-atom user-id)))
    (is (true? (domains/labels-enabled? @ds-atom user-id)))
    (is (true? (domains/ai-enabled? @ds-atom user-id)))))

(deftest test-master-off-disables-all-ai
  (testing "master OFF выключает все AI-функции"
    (domains/update-settings @ds-atom user-id
                             {:master_enabled 0 :correlations_enabled 1
                              :labels_enabled 1 :advice_enabled 1})
    (is (false? (domains/ai-enabled? @ds-atom user-id)))
    (is (false? (domains/correlations-enabled? @ds-atom user-id)))
    (is (false? (domains/advice-enabled? @ds-atom user-id)))
    (is (false? (domains/labels-enabled? @ds-atom user-id)))))

(deftest test-apply-state-label-sets-manual-label
  (testing "apply-state-label! сохраняет выбранный ярлык как ручной на последнюю запись и скрывает предложение"
    (db.entries/create-entry! @ds-atom {:user-id user-id :date "2026-08-10"
                                        :activity "walk" :effect "calm"
                                        :mood-score 4 :energy 4 :anxiety 8})
    (db/insert-finding! @ds-atom {:user-id user-id :type "label"
                                  :content {:label "тревожный интроверт" :explanation "x"}
                                  :confidence "high" :source-refs []})
    (let [applied (domains/apply-state-label! @ds-atom user-id "elevated")]
      (is (= "elevated" applied))
      (let [latest (first (jdbc/execute! @ds-atom
                                         ["SELECT state_label FROM entries ORDER BY id DESC LIMIT 1"]
                                         {:builder-fn rs/as-unqualified-kebab-maps}))]
        (is (= "state/elevated" (:state-label latest)) "ярлык записан в entries.state_label"))
      (is (= [] (db/get-findings @ds-atom user-id "label"))
          "AI-предложение скрыто после применения"))))

(deftest test-apply-state-label-only-for-own-entry
  (testing "apply-state-label! не пишет ярлык, если у пользователя нет записей"
    (is (nil? (domains/apply-state-label! @ds-atom user-id "elevated")))))

(deftest test-apply-accepts-free-text-label
  (testing "свободный текст AI-предложения сохраняется как есть"
    (db.entries/create-entry! @ds-atom {:user-id user-id :date "2026-08-11"
                                        :activity "walk" :effect "calm"
                                        :mood-score 4 :energy 4 :anxiety 8})
    (domains/apply-state-label! @ds-atom user-id "тревожный интроверт")
    (let [latest (first (jdbc/execute! @ds-atom
                                       ["SELECT state_label FROM entries ORDER BY id DESC LIMIT 1"]
                                       {:builder-fn rs/as-unqualified-kebab-maps}))]
      (is (= "тревожный интроверт" (:state-label latest))))))

(deftest test-dismiss-label-proposal-hides-finding
  (testing "dismiss-label-proposal! скрывает label-находки без удаления"
    (db/insert-finding! @ds-atom {:user-id user-id :type "label"
                                  :content {:label "п" :explanation "x"}
                                  :confidence "high" :source-refs []})
    (domains/dismiss-label-proposal! @ds-atom user-id)
    (is (= [] (db/get-findings @ds-atom user-id "label")))
    (let [row (first (jdbc/execute! @ds-atom
                                    ["SELECT feedback,hidden FROM ai_findings WHERE type='label'"]
                                    {:builder-fn rs/as-unqualified-maps}))]
      (is (= "already-known" (:feedback row)))
      (is (= 1 (:hidden row))))))

(deftest test-needs-crisis-response-detects-keywords
  (testing "признаки кризиса (ключевые слова) распознаются"
    (is (true? (domains/needs-crisis-response? "не хочу жить")))
    (is (not (domains/needs-crisis-response? "просто тревожный день, плохо спал"))
        "обычная жалоба — не кризис")
    (is (not (domains/needs-crisis-response? "")))))

(deftest test-novel-advice-off-by-default
  (testing "novel advice opt-in по умолчанию OFF: генерация не вызывается, модель не дёргается"
    (is (false? (domains/novel-advice-enabled? @ds-atom user-id))
        "novel-advice по умолчанию выключен")
    (let [called (atom 0)]
      (with-redefs [domains/call-chat
                    (fn [model messages] (swap! called inc)
                      "{\"advice\":\"дыхание по квадрату\"}")]
        (is (= [] (domains/generate-novel-advice @ds-atom user-id))
            "при OFF novel-совет не генерируется")
        (is (zero? @called) "call-chat не вызывается при выключенном opt-in")))))

(deftest test-novel-advice-optin-toggle-and-enabled
  (testing "allow_novel_advice: по умолчанию OFF, включается, master OFF отключает"
    (is (false? (domains/novel-advice-enabled? @ds-atom user-id)))
    (domains/update-settings @ds-atom user-id
                             {:master_enabled 1 :correlations_enabled 1
                              :labels_enabled 1 :advice_enabled 1
                              :allow_novel_advice 1})
    (is (true? (domains/novel-advice-enabled? @ds-atom user-id))
        "включить novel advice можно")
    (domains/update-settings @ds-atom user-id {:master_enabled 0})
    (is (false? (domains/novel-advice-enabled? @ds-atom user-id))
        "master OFF отключает и novel advice")))

(deftest test-novel-advice-generated-when-no-own-insights
  (testing "при opt-in и без своих advice-находок AI может предложить технику из общей базы с пометкой novel"
    (domains/update-settings @ds-atom user-id
                             {:master_enabled 1 :correlations_enabled 1
                              :labels_enabled 1 :advice_enabled 1
                              :allow_novel_advice 1})
    (with-redefs [domains/call-chat
                  (fn [_ _] "{\"advice\":\"дыхание по квадрату\",\"explanation\":\"общая DBT-техника\",\"confidence\":\"high\",\"source_refs\":[]}")]
      (let [items (domains/generate-novel-advice @ds-atom user-id)]
        (is (= 1 (count items)) "novel-совет сохранён и возвращён")
        (let [item (first items)]
          (is (= "advice" (:type item)))
          (is (true? (get-in item [:content :novel])) "совет помечен как novel (не из своих записей)"))))))

(deftest test-novel-advice-priority-own-insights
  (testing "при релевантных своих advice-находках novel не генерируется (приоритет своим)"
    (domains/update-settings @ds-atom user-id
                             {:master_enabled 1 :correlations_enabled 1
                              :labels_enabled 1 :advice_enabled 1
                              :allow_novel_advice 1})
    (db/insert-finding! @ds-atom
                        {:user-id user-id :type "advice"
                         :content {:message "дыхание из твоего инсайта" :explanation "e"}
                         :confidence "high" :source-refs []})
    (let [called (atom 0)]
      (with-redefs [domains/call-chat
                    (fn [_ _] (swap! called inc) "{\"advice\":\"новая DBT-техника\"}")]
        (is (= [] (domains/generate-novel-advice @ds-atom user-id))
            "свои советы имеют приоритет над novel")
        (is (zero? @called) "AI не вызывается, когда уже есть свои советы")))))

(deftest test-novel-advice-list-filters-novel
  (testing "list-novel-advice возвращает только novel-советы (novel=true)"
    (db/insert-finding! @ds-atom
                        {:user-id user-id :type "advice"
                         :content {:message "из своих записей" :explanation "e" :novel false}
                         :confidence "medium" :source-refs []})
    (db/insert-finding! @ds-atom
                        {:user-id user-id :type "advice"
                         :content {:message "новая DBT-техника" :explanation "x" :novel true}
                         :confidence "medium" :source-refs []})
    (let [novel (domains/list-novel-advice @ds-atom user-id)]
      (is (= 1 (count novel)) "в списке novel — только novel-находки")
      (is (true? (get-in (first novel) [:content :novel])))
      (is (= "новая DBT-техника" (get-in (first novel) [:content :message]))))))

(deftest test-chat-save-and-history
  (testing "сообщения чата сохраняются и читаются в хронологическом порядке"
    (domains/save-chat-message! @ds-atom user-id "user" "привет")
    (domains/save-chat-message! @ds-atom user-id "assistant" "привет!")
    (let [history (domains/chat-history @ds-atom user-id)]
      (is (= 2 (count history)))
      (is (= ["user" "assistant"] (mapv :role history)) "сообщения в порядке записи"))))

(deftest test-chat-reply-normal-message
  (testing "обычное сообщение получает ответ чата (AI с контекстом)"
    (with-redefs [domains/call-chat (fn [_ _] "спокойное дыхание поможет")]
      (let [reply (domains/chat-reply @ds-atom user-id [] "мне тревожно")]
        (is (= "спокойное дыхание поможет" reply))))))