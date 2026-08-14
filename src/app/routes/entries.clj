(ns app.routes.entries
  (:require [app.domains.entries :as entries]
            [app.views.entries :as views]
            [hiccup.core :as hc]))

(defn htmx-request?
  [request]
  (= "true" (get-in request [:headers "hx-request"])))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (hc/html body)})

(defn create-entry
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
  [ds request]
  (if (prefers-html? request)
    (html-response 200 (views/page request (entries/list-entries ds (get-in request [:identity :id]))))
    {:status 200
     :body (entries/list-entries ds (get-in request [:identity :id]))}))