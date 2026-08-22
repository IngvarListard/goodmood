(ns app.routes.medications
  (:require [app.domains.medications :as medications]
            [app.routes.entries :refer [htmx-request?]]
            [app.views.medications :as views]
            [hiccup2.core :refer [html]]))

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

(defn- content
  "Отрендерить свежий фрагмент #med-content (виджет, список, неактивные)
   для текущего пользователя."
  [ds request]
  (views/page-content (:anti-forgery-token request)
                      (medications/list-medications ds (user-id request))
                      (medications/get-today-slots ds (user-id request))))

(defn page
  "GET /medications — страница реестра медикаментов с виджетом сегодняшнего приёма."
  [ds request]
  (let [user-id (user-id request)]
    (html-response
     200
     (views/page request
                 (medications/list-medications ds user-id)
                 (medications/get-today-slots ds user-id)))))

(defn new-modal
  "GET /medications/new — содержимое модалки формы нового медикамента."
  [ds request]
  (html-response
   200
   (views/medication-form (:anti-forgery-token request) nil)))

(defn create-medication
  "POST /medications — создать медикамент и вернуть обновлённый фрагмент #med-content."
  [ds request]
  (let [user-id (user-id request)
        params (get-in request [:parameters :body])]
    (medications/create-medication ds user-id params)
    (html-response 201 (content ds request))))

(defn edit-modal
  "GET /medications/:id/edit — содержимое модалки формы редактирования."
  [ds request]
  (let [id (path-id request)
        med (when id (medications/get-medication ds (user-id request) id))]
    (if med
      (html-response 200 (views/medication-form (:anti-forgery-token request) med))
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn update-medication
  "POST /medications/:id — обновить медикамент (с dose-change tracking)."
  [ds request]
  (let [id (path-id request)
        params (get-in request [:parameters :body])
        updated (when id
                  (medications/update-medication ds (user-id request) id params))]
    (if updated
      (html-response 200 (content ds request))
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn deactivate
  "POST /medications/:id/deactivate — деактивировать медикамент."
  [ds request]
  (let [id (path-id request)
        med (when id (medications/deactivate-medication ds (user-id request) id))]
    (if med
      (html-response 200 (content ds request))
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn activate
  "POST /medications/:id/activate — активировать медикамент обратно."
  [ds request]
  (let [id (path-id request)
        med (when id (medications/activate-medication ds (user-id request) id))]
    (if med
      (html-response 200 (content ds request))
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn log-intake
  "POST /medications/:id/log — отметка приёма: taken/skipped — upsert,
   pending — отмена (delete). Возвращает обновлённый слот (outerHTML)."
  [ds request]
  (let [id (path-id request)
        params (get-in request [:parameters :body])
        med (when id (medications/get-medication ds (user-id request) id))
        user-id (user-id request)]
    (if-not med
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"}
      (let [{:keys [status scheduled_time log_date]} params]
        (if (= "pending" status)
          (medications/cancel-intake ds user-id id scheduled_time log_date)
          (medications/log-intake ds user-id {:medication_id id
                                              :scheduled_time scheduled_time
                                              :status status
                                              :log_date log_date}))
        (html-response
         201
         (views/intake-slot
          (:anti-forgery-token request)
          {:medication med
           :scheduled-time scheduled_time
           :log (if (= "pending" status)
                  nil
                  (medications/get-log ds user-id id scheduled_time log_date))}))))))