(ns app.routes.state-periods
  (:require [app.domains.state-periods :as periods]
            [app.views.state-periods :as views]
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

(defn start
  "POST /periods/start — начать период состояния (label + notes). Возвращает
   фрагмент active-period-indicator для свапа в #period-indicator."
  [ds request]
  (let [uid (user-id request)
        params (merge (:form-params request)
                      (:body-params request)
                      (:params request))
        label (str (or (get params "label") (get params :label) ""))]
    (if (str/blank? label)
      (html-response 400 (views/validation-error))
      (let [notes (some-> (or (get params "notes") (get params :notes)) str)
            period (periods/start-period ds uid {:label label :notes notes})]
        (html-response 200 (views/active-indicator
                            (get-in request [:anti-forgery-token])
                            period))))))

(defn end
  "POST /periods/:id/end — закрыть активный период. Возвращает фрагмент
   start-banner для свапа в #period-indicator."
  [ds request]
  (let [uid (user-id request)
        id (some-> (get-in request [:path-params :id]) parse-long)]
    (periods/close-period ds uid id)
    (html-response 200 (views/start-banner (get-in request [:anti-forgery-token])))))