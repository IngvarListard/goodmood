(ns app.routes.ai
  (:require [app.domains.ai :as ai]
            [app.domains.entries :as entries]
            [app.views.ai :as views]
            [clojure.string :as str]
            [hiccup2.core :refer [html]]))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})

(defn- user-id
  [request]
  (get-in request [:identity :id]))

(defn- csrf-token
  [request]
  (:anti-forgery-token request))

(defn- latest-state-label
  "Вернуть state_label последней записи пользователя (raw string) или nil."
  [ds user-id]
  (when-let [latest (first (entries/list-entries ds user-id))]
    (:state-label latest)))

(defn correlations-fragment
  "POST /ai/correlations — запустить анализ корреляций и вернуть фрагмент
   секции ai-correlations (пустой при недостигнутом пороге / opt-out)."
  [ds request]
  (let [uid (user-id request)]
    (if (ai/correlations-enabled? ds uid)
      (let [_ (ai/analyze-correlations ds uid)
            findings (ai/list-correlations ds uid)]
        (html-response 200 (views/ai-correlations (csrf-token request) findings)))
      (html-response 200 nil))))

(defn state-label-fragment
  "POST /ai/label — предложить AI-ярлык (если нет ручного) и вернуть фрагмент
   ai-state-label (пустой при opt-out)."
  [ds request]
  (let [uid (user-id request)]
    (if (ai/labels-enabled? ds uid)
      (let [manual-label (latest-state-label ds uid)
            _ (ai/propose-state-label ds uid manual-label)
            findings (ai/list-labels ds uid)]
        (html-response 200 (views/ai-state-label (csrf-token request) findings)))
      (html-response 200 nil))))

(defn advice-fragment
  "POST /ai/advice — сгенерировать совет из своих инсайтов и вернуть фрагмент
   ai-advice (пустой при opt-out)."
  [ds request]
  (let [uid (user-id request)]
    (if (ai/advice-enabled? ds uid)
      (let [_ (ai/generate-advice-from-insights ds uid)
            findings (ai/list-advice ds uid)]
        (html-response 200 (views/ai-advice (csrf-token request) findings)))
      (html-response 200 nil))))

(defn feedback
  "POST /ai/findings/:id/feedback — сохранить feedback пользователя и скрыть
   находку. Параметр feedback приходит из form/body: \"irrelevant\",
   \"already-known\" или \"report\"."
  [ds request]
  (let [uid (user-id request)
        id (some-> (get-in request [:path-params :id]) parse-long)
        feedback (get (merge (:form-params request)
                             (:body-params request)
                             (:params request))
                      :feedback)
        result (when (and id feedback) (ai/give-feedback ds uid id feedback))]
    (if result
      {:status 200
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body ""}
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn apply-label
  "POST /ai/label/apply — принять/изменить/отклонить AI-предложение ярлыка.
   label: выбранный state_label (стандартный ключ или текст AI-предложения);
   reject=true — отклонить. Сохраняет ручной ярлык на последнюю запись
   (последнее слово — за пользователем). Возвращает обновлённый виджет."
  [ds request]
  (let [uid (user-id request)
        params (merge (:form-params request)
                      (:body-params request)
                      (:params request))
        label (or (get params "label") (get params :label))
        reject? (let [v (or (get params "reject") (get params :reject))]
                  (or (= "true" (str v)) (= "1" (str v))))]
    (if reject?
      (do (ai/dismiss-label-proposal! ds uid)
          (html-response 200 nil))
      (if (and label (seq (str label)))
        (do (ai/apply-state-label! ds uid (str label))
            (html-response 200 (views/ai-state-label (csrf-token request) [] (str label))))
        {:status 400
         :headers {"Content-Type" "text/plain; charset=utf-8"}
         :body "Bad Request"}))))

(defn settings-update
  "POST /settings/ai — обновить настройки AI (master + per-function).
   Параметры из form-encoded или JSON."
  [ds request]
  (let [uid (user-id request)
        params (merge (:form-params request)
                      (:body-params request)
                      (:params request))]
    (ai/update-settings ds uid params)
    (html-response 200 (views/ai-settings-saved))))

;; ──────────────────────────────────────────────────────────────
;; Novel advice (Decision 6.2)
;; ──────────────────────────────────────────────────────────────

(defn novel-advice-fragment
  "POST /ai/novel-advice — сгенерировать novel-совет (если opt-in включён) и
   вернуть фрагмент ai-novel-advice (пустой при opt-out / наличии своих)."
  [ds request]
  (let [uid (user-id request)]
    (if (ai/novel-advice-enabled? ds uid)
      (let [_ (ai/generate-novel-advice ds uid)
            findings (ai/list-novel-advice ds uid)]
        (html-response 200 (views/ai-novel-advice (csrf-token request) findings)))
      (html-response 200 nil))))

;; ──────────────────────────────────────────────────────────────
;; AI-чат (Decision 6.3)
;; ──────────────────────────────────────────────────────────────

(defn chat-panel
  "GET /ai/chat — фрагмент панели чата (открывается по кнопке на /feed).
   Показывает disclaimer при первом открытии (по флагу сессии)."
  [ds request]
  (let [uid (user-id request)
        show-disclaimer? (not (get-in (:session request)
                                      [:ai-chat-disclaimer-dismissed]))]
    (html-response 200 (views/chat-panel (csrf-token request)
                                         show-disclaimer?
                                         (ai/chat-history ds uid)))))

(defn chat-send
  "POST /ai/chat — отправить сообщение: сохранить user-сообщение, получить
   ответ ассистента (контекст: роза + записи + свои инсайты), сохранить его и
   вернуть обновлённый фрагмент #chat-response. При кризис-детекции добавляется
   напоминание о профессиональной помощи (guardrail)."
  [ds request]
  (let [uid (user-id request)
        params (merge (:form-params request)
                      (:body-params request)
                      (:params request))
        message (str (or (get params "message") (get params :message) ""))
        trimmed (str/trim message)]
    (if (str/blank? trimmed)
      {:status 400
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Bad Request"}
      (let [history (ai/chat-history ds uid)
            reply (ai/chat-reply ds uid history trimmed)
            _ (ai/save-chat-message! ds uid "user" trimmed)
            _ (when reply (ai/save-chat-message! ds uid "assistant" reply))
            crisis? (ai/needs-crisis-response? trimmed)]
        (html-response 200 (views/chat-response {:crisis? crisis?
                                                 :messages (ai/chat-history ds uid)}))))))

(defn chat-disclaimer
  "POST /ai/chat/disclaimer — подтвердить disclaimer (пометить в сессии) и
   вернуть панель чата без него."
  [ds request]
  (let [uid (user-id request)
        csrf (csrf-token request)]
    (-> (html-response 200 (views/chat-panel csrf false (ai/chat-history ds uid)))
        (assoc :session (assoc (or (:session request) {})
                               :ai-chat-disclaimer-dismissed true)))))

;; ──────────────────────────────────────────────────────────────
;; Episode warnings (Фаза 7) — guardrails, opt-in, feedback
;; ──────────────────────────────────────────────────────────────

(defn episode-warning-fragment
  "GET /ai/episode-warning — фрагмент предупреждения/кризис-баннера для /feed
   (поллинг). Пустой — ничего не рендерится (слот исчезает по outerHTML)."
  [ds request]
  (let [uid (user-id request)]
    (html-response 200 (views/episode-warning-fragment
                        (csrf-token request)
                        (ai/current-episode-signal ds uid)))))

(defn episode-warning-feedback
  "POST /ai/episode-warning/:id/feedback — сохранить feedback («ложная тревога»)
   и скрыть предупреждение. Параметр feedback из form/body."
  [ds request]
  (let [uid (user-id request)
        id (some-> (get-in request [:path-params :id]) parse-long)
        feedback (get (merge (:form-params request)
                             (:body-params request)
                             (:params request))
                      :feedback)
        result (when (and id feedback)
                 (ai/give-episode-warning-feedback ds uid id feedback))]
    (if result
      {:status 200
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body ""}
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn episode-warning-dismiss
  "POST /ai/episode-warning/:id/dismiss — скрыть предупреждение без feedback."
  [ds request]
  (let [uid (user-id request)
        id (some-> (get-in request [:path-params :id]) parse-long)
        result (when id (ai/dismiss-episode-warning ds uid id))]
    (if result
      {:status 200
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body ""}
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn episode-warning-disable
  "POST /ai/episode-warning/disable — выключить предупреждения об эпизодах
   в один клик (opt-out persists, активные предупреждения скрываются).
   Возвращает пустой фрагмент (скрывает)."
  [ds request]
  (let [uid (user-id request)]
    (ai/disable-episode-warnings! ds uid)
    (html-response 200 nil)))