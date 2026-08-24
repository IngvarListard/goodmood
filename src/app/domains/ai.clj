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

(def min-days-threshold
  "Порог: анализ корреляций запускается при >= 14 дней записей."
  14)

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
    (db/get-findings ds user-id "advice")))

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
  "Список не скрытых AI-советов пользователя."
  [ds user-id]
  (db/get-findings ds user-id "advice"))

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
  "Нужно ли сгенерировать AI-совет: есть свои инсайты и ещё нет
   сохранённого advice-finding."
  [ds user-id]
  (and (seq (insights/get-insights ds user-id))
       (empty? (db/get-findings ds user-id "advice"))))

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
  {:master-enabled       1
   :correlations-enabled 1
   :labels-enabled       1
   :advice-enabled       1})

(defn get-settings
  "Настройки AI пользователя: сохранённые, либо значения по умолчанию
   (всё включено), если строки ещё нет."
  [ds user-id]
  (or (db/get-ai-settings ds user-id)
      (merge default-ai-settings {:user-id user-id})))

(defn update-settings
  "Обновить настройки AI пользователя. Принимает мапу с ключами
   master_enabled / correlations_enabled / labels_enabled / advice_enabled
   (из form/JSON). Возвращает обновлённые настройки."
  [ds user-id params]
  (let [master (coerce-enabled (or (get params :master_enabled)
                                   (get params "master_enabled")))
        correlations (coerce-enabled (or (get params :correlations_enabled)
                                         (get params "correlations_enabled")))
        labels (coerce-enabled (or (get params :labels_enabled)
                                   (get params "labels_enabled")))
        advice (coerce-enabled (or (get params :advice_enabled)
                                   (get params "advice_enabled")))]
    (db/set-ai-settings! ds user-id
                         {:master-enabled       master
                          :correlations-enabled correlations
                          :labels-enabled       labels
                          :advice-enabled       advice})))

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