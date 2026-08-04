(ns app.domains.entries.handlers
  (:require [app.domains.entries.db :as db]
            [app.domains.entries.views :as views]
            [hiccup.core :as hc]))

(def create-entry-schema
  [:map
   [:activity :string]
   [:effect :string]
   [:mood_score [:int {:min 0 :max 10}]]
   [:sleep_hours [:maybe :double]]])

(defn- today []
  (str (java.time.LocalDate/now)))

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
        entry (db/create-entry! ds {:date (today)
                                    :activity activity
                                    :effect effect
                                    :mood-score mood_score
                                    :sleep-hours sleep_hours})]
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
    (html-response 200 (views/page (db/get-entries ds)))
    {:status 200
     :body (db/get-entries ds)}))
