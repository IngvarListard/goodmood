(ns app.routes.insights
  (:require [app.domains.entries :as entries]
            [app.domains.insights :as domains]
            [app.i18n :as i18n]
            [app.views.insights :as views]
            [clojure.string :as str]
            [hiccup2.core :refer [html]]
            [ring.util.response :as response]))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})

(defn- user-id
  [request]
  (get-in request [:identity :id]))

(defn- path-id
  [request]
  (some-> (get-in request [:path-params :id])
          parse-long))

(defn- csrf-token
  [request]
  (:anti-forgery-token request))

(defn page
  "GET /insights — список инсайтов с htmx-фильтром по category."
  [ds request]
  (let [uid (user-id request)
        category (get-in request [:query-params "category"])
        insights (domains/list-insights ds uid category)]
    (html-response 200 (views/list-page request insights category))))

(defn new-page
  "GET /insights/new — форма создания. Предзаполнение из query params."
  [_ds request]
  (let [query-params (:query-params request)
        state-label (get query-params "state_label")
        entry-id (some-> (get query-params "entry_id") parse-long)]
    (html-response 200 (views/new-page request {:state-label state-label
                                                 :entry-id entry-id}))))

(defn- state-context-seed
  "Сформировать предзаполненный context из state_label."
  [state-label]
  (when (and state-label (seq state-label))
    (str "Состояние: " (i18n/t (keyword "state" state-label)))))

(defn advice-items
  "GET /insights/:id/advice-items — фрагмент «все советы» для раскрытия
   в карточке списка (htmx-get на кнопке «…ещё N советов»)."
  [ds request]
  (let [id (path-id request)
        insight (when id (domains/get-insight ds (user-id request) id))]
    (if insight
      (html-response 200 (views/advice-items-fragment insight))
      {:status 404 :body "Not found"})))

(defn show
  "GET /insights/:id — полный инсайт."
  [ds request]
  (let [id (path-id request)
        insight (when id (domains/get-insight ds (user-id request) id))]
    (if insight
      (html-response 200 (views/show-page request insight))
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn create
  "POST /insights — создать инсайт. При успехе HX-Redirect на /insights,
   при ошибке — validation-error-fragment в #form-error."
  [ds request]
  (let [uid (user-id request)
        params (get-in request [:parameters :body])
        advice (get params :advice_to_self)]
    (try
      (let [created (domains/create-insight ds uid params)]
        {:status 201
         :headers {"HX-Redirect" "/insights"}
         :body ""})
      (catch clojure.lang.ExceptionInfo e
        (let [errors (-> e ex-data :errors)]
          (html-response 200 (views/validation-error-fragment nil)))))))

(defn edit-context
  "GET /insights/:id/edit-context — edit-форма context."
  [ds request]
  (let [id (path-id request)
        insight (when id (domains/get-insight ds (user-id request) id))]
    (if insight
      (html-response 200 (views/context-edit-fragment (csrf-token request) insight))
      {:status 404 :body "Not found"})))

(defn update-context
  "POST /insights/:id/context — сохранить context, свап на read-блок."
  [ds request]
  (let [id (path-id request)
        uid (user-id request)
        params (:body-params request)
        value (:context params)]
    (if-let [updated (domains/update-field ds uid id :context value)]
      (html-response 200 (views/context-read-block updated))
      {:status 404 :body "Not found"})))

(defn edit-advice
  "GET /insights/:id/edit-advice — edit-форма advice."
  [ds request]
  (let [id (path-id request)
        insight (when id (domains/get-insight ds (user-id request) id))]
    (if insight
      (html-response 200 (views/advice-edit-fragment (csrf-token request) insight))
      {:status 404 :body "Not found"})))

(defn update-advice
  "POST /insights/:id/advice — сохранить advice, свап на read-блок."
  [ds request]
  (let [id (path-id request)
        uid (user-id request)
        params (:body-params request)
        advice (:advice_to_self params)]
    (if-let [updated (domains/update-field ds uid id :advice_to_self advice)]
      (html-response 200 (views/advice-read-block updated))
      {:status 404 :body "Not found"})))

(defn edit-identity
  "GET /insights/:id/edit-identity — edit-форма identity."
  [ds request]
  (let [id (path-id request)
        insight (when id (domains/get-insight ds (user-id request) id))]
    (if insight
      (html-response 200 (views/identity-edit-fragment (csrf-token request) insight))
      {:status 404 :body "Not found"})))

(defn update-identity
  "POST /insights/:id/identity — сохранить identity, свап на read-блок."
  [ds request]
  (let [id (path-id request)
        uid (user-id request)
        params (:body-params request)
        value (:identity params)]
    (if-let [updated (domains/update-field ds uid id :identity value)]
      (html-response 200 (views/identity-read-block updated))
      {:status 404 :body "Not found"})))

(defn delete
  "DELETE /insights/:id — hard delete, редирект на /insights."
  [ds request]
  (let [id (path-id request)
        uid (user-id request)]
    (domains/delete-insight ds uid id)
    {:status 200
     :headers {"HX-Redirect" "/insights"}
     :body ""}))

(defn advice-item
  "GET /insights/advice-item — фрагмент нового пункта advice для htmx beforeend."
  [_request]
  (html-response 200 (views/advice-item-fragment)))
