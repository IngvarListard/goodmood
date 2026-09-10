(ns app.routes.html
  "Общий HTML-ответ для route-хендлеров: hiccup-данные в ring-ответ."
  (:require [hiccup2.core :refer [html]]))

(defn html-response
  "Ring-ответ с отрендеренным hiccup-телом."
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})
