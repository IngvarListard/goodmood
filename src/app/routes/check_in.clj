(ns app.routes.check-in
  (:require [app.domains.entries :as entries]
            [app.views.check-in :as views]
            [clojure.string :as str]
            [hiccup2.core :refer [html]]))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})

(defn- raw-state-label
  "Вернуть raw state_label строки (без префикса :state/), если задан;
   иначе вычислить rule-based из осей."
  [entry]
  (if-let [l (:state-label entry)]
    (str/replace l "state/" "")
    (-> (entries/state-label entry) name (str/replace "state/" ""))))

(defn- soft-mode?
  "Вернуть true, если последняя запись пользователя в состоянии low/mixed —
   предложить мягкий режим (Decision 14.4)."
  [ds user-id]
  (if-let [latest (first (entries/list-entries ds user-id))]
    (contains? #{"low" "mixed"} (raw-state-label latest))
    false))

(defn page
  "Показать страницу создания записи (/check-in) для аутентифицированного
   пользователя. При последней записи low/mixed показывает баннер мягкого
   режима над формой."
  [ds request]
  (let [user-id (get-in request [:identity :id])
        soft? (soft-mode? ds user-id)]
    (html-response 200 (views/page request soft?))))