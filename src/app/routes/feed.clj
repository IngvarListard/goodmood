(ns app.routes.feed
  (:require [app.domains.entries :as entries]
            [app.domains.insights :as insights]
            [app.views.feed :as views]
            [clojure.string :as str]
            [hiccup2.core :refer [html]]))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})

(defn- raw-state-label
  "Вернуть raw state_label строки (без префикса :state/), если он задан;
   иначе вычислить rule-based из осей."
  [entry]
  (or (:state-label entry)
      (-> (entries/state-label entry) name (str/replace "state/" ""))))

(defn page
  "Показать ленту записей («мой день») для аутентифицированного пользователя.
   Под hero-карточкой рендерится виджет инсайтов: 1 релевантный по
   state_label последней записи сегодня, или мягкий онбординг (OQ3)."
  [ds request]
  (let [user-id (get-in request [:identity :id])
        entries (entries/list-entries ds user-id)
        today (str (java.time.LocalDate/now))
        today-entries (filter #(= today (:date %)) entries)
        latest (first today-entries)
        state-label (when latest (raw-state-label latest))
        insight (when state-label
                  (insights/matching-insight ds user-id state-label))]
    (html-response 200 (views/page request entries state-label insight))))