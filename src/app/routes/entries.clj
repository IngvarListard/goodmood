(ns app.routes.entries
  (:require [app.domains.entries :as entries]
            [app.views.entries :as views]
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
  (let [{:keys [activity effect mood_score sleep_hours]}
        (get-in request [:parameters :body])
        user-id (get-in request [:identity :id])
        entry (entries/create-entry ds user-id
                                    {:activity activity
                                     :effect effect
                                     :mood_score mood_score
                                     :sleep_hours sleep_hours})]
    (if (htmx-request? request)
      (html-response 201 (views/item entry))
      {:status 201
       :body entry})))

(defn- prefers-html?
  [request]
  (clojure.string/includes?
   (or (get-in request [:headers "accept"]) "")
   "text/html"))

(defn get-entries
  "Показать список записей настроения для аутентифицированного пользователя.
   Возвращает HTML-страницу для браузерных запросов или JSON для API."
  [ds request]
  (if (prefers-html? request)
    (html-response 200 (views/page request (entries/list-entries ds (get-in request [:identity :id]))))
    {:status 200
     :body (entries/list-entries ds (get-in request [:identity :id]))}))