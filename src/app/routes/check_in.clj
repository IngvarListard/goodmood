(ns app.routes.check-in
  (:require [app.views.check-in :as views]
            [hiccup2.core :refer [html]]))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})

(defn page
  "Показать страницу создания записи (/check-in) для аутентифицированного пользователя."
  [request]
  (html-response 200 (views/page request)))