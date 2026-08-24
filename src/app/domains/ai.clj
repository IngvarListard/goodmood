(ns app.domains.ai
  (:require [app.db.ai :as db]
            [app.db.entries :as entries]
            [app.db.insights :as insights]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [malli.core :as mc]))

(def openrouter-url
  "Endpoint OpenRouter chat/completions."
  "https://openrouter.ai/api/v1/chat/completions")

(def correlation-model
  "Модель для корреляций и ярлыков (Decision 5.1)."
  "deepseek/deepseek-chat")

(def advice-model
  "Модель для советов из своих инсайтов."
  "z-ai/glm-5.2")

(def chat-model
  "Модель для AI-чата."
  "deepseek/deepseek-chat")

(def min-days-threshold
  "Порог: анализ корреляций запускается при >= 14 дней записей."
  14)

(def min-episode-trend-days
  "Порог: анализ тренда эпизода запускается при >= 3 записей."
  3)

(def confidence-threshold
  "Порог уверенности для показа предупреждения об эпизоде (> 0.75).
   При <= 0.75 паттерн логируется (dismissed=1), но не показывается."
  0.75)

(def episode-warning-model
  "Модель для анализа тренда эпизодов (OpenRouter, deepseek)."
  "deepseek/deepseek-chat")

(declare list-advice)

(defn api-key
  "Ключ OpenRouter из окружения OPENROUTER_API_KEY или nil."
  []
  (System/getenv "OPENROUTER_API_KEY"))

;; ──────────────────────────────────────────────────────────────
;; Malli-схемы структур ответа AI
;; ──────────────────────────────────────────────────────────────

(def correlation-schema
  "Схема одной корреляционной находки (title/description/confidence)."
  [:map {:closed true}
   [:title :string]
   [:description :string]
   [:confidence [:enum "high" "medium" "low"]]])

(def label-schema
  "Схема AI-ярлыка (label/explanation/confidence)."
  [:map {:closed true}
   [:label :string]
   [:explanation :string]
   [:confidence [:enum "high" "medium" "low"]]])

(def advice-schema
  "Схема AI-совета (advice/explanation/confidence/source_refs)."
  [:map {:closed true}
   [:advice :string]
   [:explanation :string]
   [:confidence [:enum "high" "medium" "low"]]
   [:source_refs [:vector :int]]])

(def episode-warning-schema
  "Схема ответа AI для анализа тренда эпизода (type/confidence/pattern)."
  [:map {:closed true}
   [:type [:enum "depressive" "hypomanic" "none"]]
   [:confidence [:double {:min 0 :max 1}]]
   [:pattern :string]])

(defn call-chat
  "Отправить запрос в OpenRouter и вернуть текст ответа модели (или nil).
   Публичная для мокинга в тестах (with-redefs). При отсутствии ключа,
   ошибке сети или не-2xx возвращает nil (graceful)."
  [model messages]
  (let [key (api-key)]
    (when (seq key)
      (try
        (let [resp (http/post
                    openrouter-url
                    {:headers {"Authorization" (str "Bearer " key)
                               "Content-Type"  "application/json"}
                     :body (json/generate-string {:model model
                                                  :messages messages
                                                  :temperature 0.2})}
                    {:throw-exceptions false})]
          (if (= 200 (:status resp))
            (get-in (json/parse-string (:body resp) true)
                    [:choices 0 :message :content])
            (do (println "OpenRouter non-200:" (:status resp))
                nil)))
        (catch Exception e
          (println "OpenRouter call failed:" (.getMessage e))
          nil)))))

(defn- parse-findings
  "Распарсить content в вектор находок, валидированных по схеме.
   Ответ — JSON-массив объектов; одиночный объект оборачивается в массив.
   Невалидные объекты отбрасываются. Устойчив к markdown-ограждениям
   (```json ... ```), которые модели часто возвращают."
  [schema content]
  (try
    (let [cleaned (-> content
                      (str/replace #"(?s)^```(?:json)?\s*" "")
                      (str/replace #"(?s)\s*```$" "")
                      str/trim)
          data (json/parse-string cleaned true)
          items (if (sequential? data) (vec data) [data])]
      (->> items
           (filter #(mc/validate schema %))
           vec))
    (catch Exception _ [])))

(defn- format-entry
  "Компактная строка записи дневника для промпта."
  [{:keys [date energy anxiety focus mood-score sleep-hours state-label]}]
  (let [label (or state-label "-")]
    (str date " | энергия=" energy " тревога=" anxiety
         " фокус=" focus " настроение=" mood-score
         " сон=" (or sleep-hours "-") "ч состояние=" label)))

(defn- distinct-days
  "Число дней, по которым есть записи с данными сна и состояния."
  [rows]
  (->> rows
       (filter #(and (:sleep-hours %) (:energy %) (:anxiety %)))
       (map :date)
       distinct
       count))

;; ──────────────────────────────────────────────────────────────
;; Корреляции
;; ──────────────────────────────────────────────────────────────

(defn- correlation-prompt
  [rows]
  (str "Ты — помощник человека с биполярным расстройством. "
       "На основе его дневника найди до 3 значимых корреляций между "
       "факторами (сон, время суток, настроение) и состояниями розы ветров "
       "(энергия, тревога, фокус). Пользователь сам даст обратную связь, "
       "не выдумывай и не пугай.\n"
       "Верни ТОЛЬКО JSON-массив объектов: "
       "[{\"title\":\"короткое название\","
       "\"description\":\"объяснение, почему найден паттерн\","
       "\"confidence\":\"high|medium|low\"}].\n\n"
       "Дневник:\n" (str/join "\n" (map format-entry rows))))

(defn analyze-correlations
  "Запустить анализ корреляций. Если у пользователя < 14 дней данных —
   не запускает AI и возвращает текущий список находок. Иначе вызывает AI,
   валидирует ответ, сохраняет находки с confidence != low. Возвращает
   актуальный список не скрытых корреляций."
  [ds user-id]
  (let [rows (entries/get-entries ds user-id)
        days (distinct-days rows)]
    (when (>= days min-days-threshold)
      (let [content (call-chat correlation-model
                               [{:role "user" :content (correlation-prompt rows)}])]
        (when content
          (doseq [f (parse-findings correlation-schema content)]
            (when (not= "low" (:confidence f))
              (db/insert-finding! ds
                                  {:user-id user-id
                                   :type "correlation"
                                   :content {:title (:title f)
                                             :description (:description f)}
                                   :confidence (:confidence f)
                                   :source-refs []}))))))
    (db/get-findings ds user-id "correlation")))

(defn correlation-analysis-needed?
  "Нужен ли повторный анализ корреляций: накоплено >= 14 дней, но находок
   корреляций ещё нет (нет свежего кэша)."
  [ds user-id]
  (let [rows (entries/get-entries ds user-id)]
    (and (>= (distinct-days rows) min-days-threshold)
         (empty? (db/get-findings ds user-id "correlation")))))

;; ──────────────────────────────────────────────────────────────
;; AI-ярлык
;; ──────────────────────────────────────────────────────────────

(defn- state-distribution
  "Компактная сводка распределения состояний (последние N записей)."
  [rows]
  (let [recent (take 30 rows)]
    (str/join "\n" (map format-entry recent))))

(defn- label-prompt
  [rows]
  (str "Ты — психологический помощник. Опиши текущее состояние пользователя "
       "на основе розы ветров (энергия, тревога, фокус) и истории. "
       "Предложи человекочитаемый ярлык-описание (2-6 слов, по-русски).\n"
       "Верни ТОЛЬКО JSON: "
       "{\"label\":\"...\",\"explanation\":\"...\",\"confidence\":\"high|medium|low\"}.\n"
       "Дневник:\n" (state-distribution rows)))

(defn propose-state-label
  "Предложить AI-ярлык текущего состояния и сохранить находку type=label.
   Принимает только если у пользователя нет ручного ярлыка (latest.state-label
   пуст). Возвращает последний label-finding или nil."
  [ds user-id latest-state-label]
  (when-not (seq latest-state-label)
    (let [rows (entries/get-entries ds user-id)]
      (when (seq rows)
        (let [content (call-chat correlation-model
                                 [{:role "user" :content (label-prompt rows)}])
              parsed (first (parse-findings label-schema content))]
          (when parsed
            (db/insert-finding! ds
                                {:user-id user-id
                                 :type "label"
                                 :content {:label (:label parsed)
                                           :explanation (:explanation parsed)}
                                 :confidence (:confidence parsed)
                                 :source-refs []})))))
    (first (db/get-findings ds user-id "label"))))

;; ──────────────────────────────────────────────────────────────
;; Совет из своих инсайтов
;; ──────────────────────────────────────────────────────────────

(defn- advice-prompt
  [insight-rows]
  (str "Ты — помощник для биполярного расстройства. Пользователь просит "
       "совет на основе СВОИХ прошлых инсайтов (записанных им самим) "
       "в похожем контексте. Используй только его инсайты, не выдумывай. "
       "Дай один конкретный совет и объясни, почему он подходит.\n"
       "Верни ТОЛЬКО JSON: "
       "{\"advice\":\"...\",\"explanation\":\"...\",\"confidence\":\"high|medium|low\","
       "\"source_refs\":[числа-id инсайтов]}.\n"
       "Инсайты:\n"
       (str/join "\n" (map #(str "{id:" (:id %) " контекст: " (:context %)
                                 " совет: " (first (:advice-to-self %)) "}")
                           insight-rows))))

(defn generate-advice-from-insights
  "Сгенерировать совет на основе собственных инсайтов пользователя.
   Только если есть инсайты. Сохраняет находку type=advice с source_refs
   (id исходных инсайтов). Возвращает актуальный список advice-находок."
  [ds user-id]
  (let [insight-rows (insights/get-insights ds user-id)]
    (when (seq insight-rows)
      (let [content (call-chat advice-model
                               [{:role "user" :content (advice-prompt insight-rows)}])
            parsed (first (parse-findings advice-schema content))]
        (when parsed
          (db/insert-finding! ds
                              {:user-id user-id
                               :type "advice"
                               :content {:message (:advice parsed)
                                         :explanation (:explanation parsed)}
                               :confidence (:confidence parsed)
                               :source-refs (vec (:source_refs parsed))}))))
    (list-advice ds user-id)))

;; ──────────────────────────────────────────────────────────────
;; Guardrails и feedback
;; ──────────────────────────────────────────────────────────────

(def feedback-types
  "Допустимые значения feedback (по сценариям Фазы 5)."
  #{"irrelevant" "already-known" "report"})

(defn give-feedback
  "Сохранить фидбек пользователя и скрыть находку (hidden=1). Возвращает
   обновлённую находку или nil (чужая находка / несуществующий id)."
  [ds user-id finding-id feedback]
  {:pre [(contains? feedback-types feedback)]}
  (db/set-feedback! ds user-id finding-id feedback))

(defn list-correlations
  "Список не скрытых корреляций пользователя."
  [ds user-id]
  (db/get-findings ds user-id "correlation"))

(defn list-labels
  "Список не скрытых AI-ярлыков пользователя (последний первый)."
  [ds user-id]
  (db/get-findings ds user-id "label"))

(defn list-advice
  "Список не скрытых AI-советов пользователя из своих инсайтов
   (novel-советы исключаются — они помечены content.novel=true)."
  [ds user-id]
  (->> (db/get-findings ds user-id "advice")
       (remove #(get-in % [:content :novel]))
       vec))

(defn label-analysis-needed?
  "Нужно ли предложить AI-ярлык: есть записи, нет ручного ярлыка и ещё нет
   сохранённого label-finding."
  [ds user-id latest-state-label]
  (and (not (seq latest-state-label))
       (seq (entries/get-entries ds user-id))
       (empty? (db/get-findings ds user-id "label"))))

(def standard-labels
  "Список стандартных state_label розы ветров (raw ключи, без :state/)."
  #{"mixed" "anxiety" "elevated" "low" "balanced" "neutral"})

(defn- normalize-label
  "Привести выбранный ярлык к формату хранения в entries.state_label.
   Стандартные ключи хранятся как 'state/<key>' (как и rule-based), свободный
   текст AI-предложения — как есть."
  [label]
  (if (contains? standard-labels label)
    (str "state/" label)
    label))

(defn apply-state-label!
  "Применить выбранный ярлык как ручной: обновить state_label последней записи
   пользователя (последнее слово — за пользователем). Скрывает AI-предложения
   ярлыка с feedback. Возвращает применённый label (raw ключ или текст)."
  [ds user-id label]
  (when-let [latest (first (entries/get-entries ds user-id))]
    (entries/set-state-label! ds user-id (:id latest) (normalize-label label))
    (doseq [f (db/get-findings ds user-id "label")]
      (db/set-feedback! ds user-id (:id f) "accepted"))
    label))

(defn dismiss-label-proposal!
  "Отклонить AI-предложение ярлыка: скрыть label-находки с feedback
   (уже знал) — предложение больше не показывается."
  [ds user-id]
  (doseq [f (db/get-findings ds user-id "label")]
    (db/set-feedback! ds user-id (:id f) "already-known")))

(defn advice-analysis-needed?
  "Нужно ли сгенерировать AI-совет из своих инсайтов: есть свои инсайты и ещё
   нет сохранённого своего advice-finding (novel-советы не учитываются)."
  [ds user-id]
  (and (seq (insights/get-insights ds user-id))
       (empty? (list-advice ds user-id))))

(defn all-findings
  "Все не скрытые находки пользователя (для /feed)."
  [ds user-id]
  (concat (list-correlations ds user-id)
          (list-labels ds user-id)
          (list-advice ds user-id)))

;; ──────────────────────────────────────────────────────────────
;; Настройки AI
;; ──────────────────────────────────────────────────────────────

(defn- coerce-enabled
  "Привести значение в 0/1 (INTEGER для БД). nil/пусто/false → 0, иначе 1."
  [v]
  (if (or (nil? v) (= v "false") (= v "") (= v "0") (= v 0) (false? v))
    0
    1))

(def ^:private default-ai-settings
  {:master-enabled        1
   :correlations-enabled  1
   :labels-enabled        1
   :advice-enabled        1
   :allow-novel-advice    0
   :episode-warning-enabled 0})

(defn get-settings
  "Настройки AI пользователя: сохранённые, либо значения по умолчанию
   (всё включено), если строки ещё нет."
  [ds user-id]
  (or (db/get-ai-settings ds user-id)
      (merge default-ai-settings {:user-id user-id})))

(defn update-settings
  "Обновить настройки AI пользователя. Принимает мапу с ключами
   master_enabled / correlations_enabled / labels_enabled / advice_enabled /
   allow_novel_advice / episode_warning_enabled (из form/JSON). Возвращает
   обновлённые настройки."
  [ds user-id params]
  (let [master (coerce-enabled (or (get params :master_enabled)
                                   (get params "master_enabled")))
        correlations (coerce-enabled (or (get params :correlations_enabled)
                                         (get params "correlations_enabled")))
        labels (coerce-enabled (or (get params :labels_enabled)
                                   (get params "labels_enabled")))
        advice (coerce-enabled (or (get params :advice_enabled)
                                   (get params "advice_enabled")))
        novel (coerce-enabled (or (get params :allow_novel_advice)
                                  (get params "allow_novel_advice")))
        episode-warning (coerce-enabled (or (get params :episode_warning_enabled)
                                            (get params "episode_warning_enabled")))]
    (db/set-ai-settings! ds user-id
                         {:master-enabled        master
                          :correlations-enabled  correlations
                          :labels-enabled        labels
                          :advice-enabled        advice
                          :allow-novel-advice    novel
                          :episode-warning-enabled episode-warning})))

(defn ai-enabled?
  "Включён ли master-toggle AI для пользователя."
  [ds user-id]
  (not= 0 (:master-enabled (get-settings ds user-id))))

(defn correlations-enabled?
  "Включены ли корреляции (и master-toggle)."
  [ds user-id]
  (and (ai-enabled? ds user-id)
       (not= 0 (:correlations-enabled (get-settings ds user-id)))))

(defn labels-enabled?
  "Включены ли AI-ярлыки (и master-toggle)."
  [ds user-id]
  (and (ai-enabled? ds user-id)
       (not= 0 (:labels-enabled (get-settings ds user-id)))))

(defn advice-enabled?
  "Включены ли AI-советы (и master-toggle)."
  [ds user-id]
  (and (ai-enabled? ds user-id)
       (not= 0 (:advice-enabled (get-settings ds user-id)))))

;; ──────────────────────────────────────────────────────────────
;; Novel advice (Decision 6.2) — opt-in, помечены «не из твоих записей»
;; ──────────────────────────────────────────────────────────────

(defn novel-advice-enabled?
  "Включены ли novel-советы (master-toggle + allow_novel_advice)."
  [ds user-id]
  (and (ai-enabled? ds user-id)
       (not= 0 (:allow-novel-advice (get-settings ds user-id)))))

(defn- novel-prompt
  "Промпт novel-совета: общая техника DBT/CBT, не на основе записей."
  []
  (str "Ты — внимательный помощник для человека с биполярным расстройством. "
       "Предложи один конкретный приём самопомощи из общей практики (DBT/CBT): "
       "навык толерантности к дистрессу, переснижение мыслей или копинг-стратегию. "
       "Это НЕ основано на его записях — общая техника. Дай краткий совет и объясни, "
       "почему он работает.\n"
       "Верни ТОЛЬКО JSON: "
       "{\"advice\":\"...\",\"explanation\":\"...\",\"confidence\":\"high|medium|low\"}."))

(defn list-novel-advice
  "Список novel-советов пользователя (content.novel=true)."
  [ds user-id]
  (->> (db/get-findings ds user-id "advice")
       (filter #(get-in % [:content :novel]))
       vec))

(defn generate-novel-advice
  "Сгенерировать novel-совет (общая DBT/CBT техника), если он opt-in включён и
   нет своих advice-находок (приоритет своим, Decision 6.2). Сохраняет находку
   type=advice с content.novel=true. Возвращает актуальный список novel-советов."
  [ds user-id]
  (when (and (novel-advice-enabled? ds user-id)
             (empty? (list-advice ds user-id)))
    (let [content (call-chat advice-model
                             [{:role "user" :content (novel-prompt)}])
          parsed (first (parse-findings advice-schema content))]
      (when parsed
        (db/insert-finding! ds
                            {:user-id user-id
                             :type "advice"
                             :content {:message (:advice parsed)
                                       :explanation (:explanation parsed)
                                       :novel true}
                             :confidence (:confidence parsed)
                             :source-refs []}))))
  (list-novel-advice ds user-id))

(defn novel-analysis-needed?
  "Нужно ли сгенерировать novel-совет: опция включена, нет своих советов и ещё
   нет сохранённого novel-совета."
  [ds user-id]
  (and (novel-advice-enabled? ds user-id)
       (empty? (list-advice ds user-id))
       (empty? (list-novel-advice ds user-id))))

;; ──────────────────────────────────────────────────────────────
;; AI-чат (Decision 6.3) + кризис-гвардиейл
;; ──────────────────────────────────────────────────────────────

(def crisis-keywords
  "Ключевые слова кризиса (ru/en) — при совпадении ответ включает напоминание
   о профессиональной помощи (телефон доверия / 112)."
  #{"не хочу жить" "не хочу больше жить" "покончить с собой" "покончить с жизнью"
    "суицид" "самоубийство" "хочу умереть" "лучше не существовать" "исчезнуть"
    "навредить себе" "себе навредить" "kill myself" "suicide" "don't want to live"
    "want to die" "end it all" "self harm" "self-harm" "hurt myself" "cut myself"})

(defn needs-crisis-response?
  "Вернуть true, если текст сообщения содержит признаки кризиса (ключ. слова)."
  [text]
  (when (and text (seq (str text)))
    (let [lower (str/lower-case (str text))]
      (boolean (some #(str/includes? lower %) crisis-keywords)))))

(defn save-chat-message!
  "Сохранить сообщение чата (role: user|assistant)."
  [ds user-id role content]
  (db/save-message! ds {:user-id user-id :role role :content content}))

(defn chat-history
  "Последние n сообщений чата пользователя в хронологическом порядке."
  ([ds user-id]
   (db/get-messages ds user-id 8))
  ([ds user-id n]
   (db/get-messages ds user-id n)))

(defn- rose-line
  "Компактная строка розы ветров последней записи."
  [entry]
  (when entry
    (str "Роза ветров последней записи: энергия=" (or (:energy entry) "-")
         " тревога=" (or (:anxiety entry) "-")
         " фокус=" (or (:focus entry) "-")
         " настроение=" (or (:mood-score entry) "-"))))

(defn- entry-line
  "Компактная строка записи для контекста чата."
  [{:keys [date energy anxiety focus mood-score sleep-hours note]}]
  (str date " | энергия=" (or energy "-")
       " тревога=" (or anxiety "-")
       " фокус=" (or focus "-")
       " настроение=" (or mood-score "-")
       " сон=" (or sleep-hours "-")
       (when (seq note) (str " · " note))))

(defn- insight-line
  "Компактная строка инсайта (контекст → советы)."
  [insight]
  (str (:context insight) " → " (str/join " · " (take 3 (:advice-to-self insight)))))

(defn- chat-context
  "Собрать текстовый контекст чата: роза + последние записи + свои инсайты."
  [ds user-id]
  (let [rows (entries/get-entries ds user-id)
        insights-rows (insights/get-insights ds user-id)]
    (str/join "\n"
              (concat
               [(rose-line (first rows))]
               (map entry-line (take 5 rows))
               [(str "Свои инсайты:\n"
                     (str/join "\n" (map insight-line (take 5 insights-rows))))]))))

(defn chat-messages
  "Сформировать сообщения модели: system-контекст (роза + записи + инсайты),
   история и текущее сообщение пользователя."
  [ds user-id history user-message]
  (let [context (chat-context ds user-id)]
    (concat
     [{:role :system
       :content (str "Ты — тёплый поддерживающий помощник для человека с биполярным "
                     "расстройством. Отвечай кратко и по-русски. Опирайся на контекст "
                     "и его собственные инсайты, не выдумывай чужой опыт. Если в его "
                     "словах есть признаки кризиса — напомни про профессиональную помощь.\n\n"
                     "КОНТЕКСТ ПОЛЬЗОВАТЕЛЯ:\n" context)}]
     (map #(select-keys % [:role :content]) history)
     [{:role :user :content user-message}])))

(defn chat-reply
  "Получить ответ ассистента на сообщение пользователя (текст) или nil.
   history — вектор сообщений из ai_chat_messages (без текущего)."
  [ds user-id history user-message]
  (call-chat chat-model (chat-messages ds user-id history user-message)))

;; ──────────────────────────────────────────────────────────────
;; Episode warnings (Фаза 7) — opt-in, guardrails, trend analysis
;; ──────────────────────────────────────────────────────────────

(defn episode-warning-enabled?
  "Включены ли предупреждения об эпизодах (master-toggle + opt-in)."
  [ds user-id]
  (and (ai-enabled? ds user-id)
       (not= 0 (:episode-warning-enabled (get-settings ds user-id)))))

(defn set-episode-warning-enabled!
  "Включить/выключить предупреждения об эпизодах (opt-in). Сохраняет текущие
   настройки, меняя только episode_warning_enabled. Возвращает настройки."
  [ds user-id enabled]
  (let [s (get-settings ds user-id)]
    (db/set-ai-settings! ds user-id
                         {:master-enabled       (:master-enabled s)
                          :correlations-enabled (:correlations-enabled s)
                          :labels-enabled       (:labels-enabled s)
                          :advice-enabled       (:advice-enabled s)
                          :allow-novel-advice   (:allow-novel-advice s)
                          :episode-warning-enabled (if enabled 1 0)})))

(defn disable-episode-warnings!
  "Выключить предупреждения об эпизодах в один клик: снять opt-out и скрыть
   все активные предупреждения (dismissed=1), чтобы после повторного
   включения не всплывали устаревшие."
  [ds user-id]
  (doseq [w (db/get-warnings ds user-id)]
    (db/dismiss-warning! ds user-id (:id w)))
  (set-episode-warning-enabled! ds user-id false))

(defn latest-note-crisis?
  "Проверить последние записи на кризис-ключевые слова в заметках (note).
   Кризис приоритетнее предупреждения об эпизоде."
  [ds user-id]
  (boolean (some #(needs-crisis-response? (:note %))
                 (entries/get-entries ds user-id))))

(defn- avg-of
  "Среднее значение ключа по записям (nil-safe)."
  [key rows]
  (let [vals (keep key rows)]
    (when (seq vals)
      (double (/ (reduce + vals) (count vals))))))

(defn- trend-summary
  "Числовая сводка тренда: средние по последним 3 и предыдущим 3 записям.
   Делает выраженные тренды видимыми для модели."
  [rows]
  (let [recent (take 3 rows)
        prior  (take 3 (drop 3 rows))
        fmt    (fn [label key]
                 (str label "=" (avg-of key recent) " (раньше: " (avg-of key prior) ")"))]
    (->> [(fmt "энергия" :energy)
          (fmt "тревога" :anxiety)
          (fmt "настроение" :mood-score)
          (fmt "сон" :sleep-hours)]
         (str/join ", "))))

(defn- episode-trend-prompt
  "Промпт анализа тренда: числовая сводка (последние vs предыдущие) +
   компактный список записей. Просим модель ставить высокую уверенность
   честно — только при явном устойчивом тренде."
  [rows]
  (str "Ты — внимательный помощник человека с биполярным расстройством. "
       "Посмотри на числовую сводку тренда его дневника и определи, похожи ли "
       "последние дни на начало депрессивного (спад: энергия/настроение падает, "
       "сон нарушен) или маниакального / гипоманиакального (подъём: энергия и "
       "настроение растут, сон резко уменьшается) эпизода. Это НЕ диагноз — "
       "просто наблюдение по данным. Не преувеличивай и не выдумывай.\n"
       "Оцени уверенность честно: для явного устойчивого тренда (например "
       "энергия растёт по нарастающей, сон снижается) ставь confidence > 0.75; "
       "для слабых или смешанных данных — ниже, не рискуй.\n"
       "Верни ТОЛЬКО JSON: {\"type\":\"depressive|hypomanic|none\","
       "\"confidence\":0.0-1.0,\"pattern\":\"короткое объяснение паттерна "
       "по-русски, напр.: последние 3 дня энергия растёт, сон падает\"}.\n"
       "Числовая сводка (последние 3 vs предыдущие 3):\n" (trend-summary rows)
       "\nЗаписи (свежие сверху):\n" (str/join "\n" (map format-entry rows))))

(defn analyze-episode-trend
  "Проанализировать тренд последних записей. При выраженном паттерне
   (type != none и confidence > 0.75) сохраняет предупреждение (dismissed=0).
   При низкой уверенности (type != none, confidence <= 0.75) паттерн
   логируется (dismissed=1 — маркер «не показано»). type=none — ничего не
   сохраняется. Возвращает активное предупреждение или nil.
   Записи из БД приходят свежие сверху (created_at DESC) — для анализа тренда
   переворачиваем в хронологическом порядке (старые → новые), чтобы восходящий
   тренд был виден модели как рост."
  [ds user-id]
  (let [rows (->> (entries/get-entries ds user-id) reverse vec)]
    (when (>= (count rows) min-episode-trend-days)
      (when-let [content (call-chat episode-warning-model
                                    [{:role "user" :content (episode-trend-prompt rows)}])]
        (when-let [parsed (first (parse-findings episode-warning-schema content))]
          (let [{:keys [type confidence pattern]} parsed]
            (when (not= "none" type)
              (db/insert-warning! ds
                                  {:user-id             user-id
                                   :type                type
                                   :pattern-description pattern
                                   :confidence          confidence
                                   :dismissed           (if (> confidence confidence-threshold) 0 1)}))))))
    (first (db/get-warnings ds user-id))))

(defn episode-warning-needed?
  "Нужно ли запускать анализ тренда: opt-in, нет активного предупреждения
   (last warning отсутствует/устарел)."
  [ds user-id]
  (and (episode-warning-enabled? ds user-id)
       (empty? (db/get-warnings ds user-id))))

(defn episode-analysis-needed?
  "Нужно ли запустить анализ тренда: opt-in, нет активного предупреждения и
   нет кризис-сигналов (кризис приоритетнее паттерна)."
  [ds user-id]
  (and (episode-warning-needed? ds user-id)
       (not (latest-note-crisis? ds user-id))))

(defn current-episode-signal
  "Сигнал для /feed: кризис приоритетнее предупреждения.
   Возвращает {:crisis? bool :enabled? bool :warning warning-or-nil}."
  [ds user-id]
  (if (latest-note-crisis? ds user-id)
    {:crisis? true :enabled? false :warning nil}
    (let [enabled? (episode-warning-enabled? ds user-id)]
      {:crisis? false
       :enabled? enabled?
       :warning (when enabled? (first (db/get-warnings ds user-id)))})))

(def episode-warning-feedback-types
  "Допустимые feedback предупреждений об эпизоде (Фаза 7)."
  #{"false_alarm"})

(defn give-episode-warning-feedback
  "Сохранить feedback («ложная тревога») и скрыть предупреждение
   (dismissed=1). Возвращает обновлённое предупреждение или nil."
  [ds user-id warning-id feedback]
  {:pre [(contains? episode-warning-feedback-types feedback)]}
  (db/set-warning-feedback! ds user-id warning-id feedback))

(defn dismiss-episode-warning
  "Скрыть предупреждение без feedback (dismissed=1). Возвращает строку или nil."
  [ds user-id warning-id]
  (db/dismiss-warning! ds user-id warning-id))