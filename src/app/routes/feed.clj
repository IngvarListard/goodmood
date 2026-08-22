(ns app.routes.feed
  (:require [app.domains.entries :as entries]
            [app.views.feed :as views]
            [hiccup2.core :refer [html]]))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})

(defn page
  "Показать ленту записей («мой день») для аутентифицированного пользователя."
  [ds request]
  (html-response 200 (views/page request (entries/list-entries ds (get-in request [:identity :id])))))