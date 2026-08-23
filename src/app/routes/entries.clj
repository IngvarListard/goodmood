(ns app.routes.entries
  (:require [app.domains.entries :as entries]
            [app.domains.insights :as insights]
            [app.views.entries :as views]
            [app.views.notifications :as notif-views]
            [ring.util.response :as response]
            [hiccup2.core :refer [html]]))

(defn htmx-request?
  "Вернуть true если запрос является HTMX AJAX-запросом."
  [request]
  (= "true" (get-in request [:headers "hx-request"])))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})

(defn create-entry
  "Создать новую запись настроения для аутентифицированного пользователя.
   Возвращает HTML-фрагмент для HTMX-запросов или JSON для API."
  [ds request]
  (let [{:keys [activity effect mood_score energy anxiety focus sleep_hours note template]}
        (get-in request [:parameters :body])
        user-id (get-in request [:identity :id])
        entry (entries/create-entry ds user-id
                                    {:activity activity
                                     :effect effect
                                     :mood_score mood_score
                                     :energy energy
                                     :anxiety anxiety
                                     :focus focus
                                     :sleep_hours sleep_hours
                                     :note note
                                     :template template})]
    (if (htmx-request? request)
      (html-response 201 (views/item entry))
      {:status 201
       :body entry})))

(defn get-entries
  "Старый GET /entries → постоянный редирект на /feed (лента заменила список)."
  [_ _]
  (response/redirect "/feed" 308))