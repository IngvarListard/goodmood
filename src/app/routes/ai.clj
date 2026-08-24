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