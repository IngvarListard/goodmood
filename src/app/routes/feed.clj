(ns app.routes.feed
  (:require [app.domains.entries :as entries]
            [app.domains.insights :as insights]
            [app.domains.notification-settings :as notif-domains]
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

(defn- saved-toast-insight
  "Вернуть инсайт для toast «только что сохранил», когда /feed открыт с
   ?saved=1 (редирект из /check-in после успешного сохранения). Подбирает
   релевантный инсайт по state_label последней записи сегодня."
  [ds user-id saved?]
  (when (and saved? user-id)
    (let [today (str (java.time.LocalDate/now))
          latest (first (filter #(= today (:date %))
                                (entries/list-entries ds user-id)))]
      (when latest
        (let [state-label (raw-state-label latest)]
          (when state-label
            (insights/matching-insight ds user-id state-label)))))))

(defn- summary-showing?
  "Вернуть true, если вечернюю сводку надо показать: ≥18:00, есть записи
   сегодня и sentinel last_summary_date != сегодня (Decision 14.2, OQ6)."
  [settings today today-entries]
  (let [evening (first (filter #(= "evening" (:slot %)) settings))]
    (and (>= (Integer/parseInt (subs (str (java.time.LocalTime/now)) 0 2)) 18)
         (seq today-entries)
         (or (nil? evening)
             (not= (:last-summary-date evening) today)))))

(defn page
  "Показать ленту записей («мой день») для аутентифицированного пользователя.
   Под hero-карточкой рендерится виджет инсайтов: 1 релевантный по
   state_label последней записи сегодня, или мягкий онбординг (OQ3).
   saved — флаг ?saved=1 из редиректа /check-in (показывает toast-инсайт)."
  [ds request]
  (let [user-id (get-in request [:identity :id])
        entries (entries/list-entries ds user-id)
        today (str (java.time.LocalDate/now))
        today-entries (filter #(= today (:date %)) entries)
        latest (first today-entries)
        state-label (when latest (raw-state-label latest))
        insight (when state-label
                  (insights/matching-insight ds user-id state-label))
        saved? (= "1" (get-in request [:query-params "saved"]))
        toast-insight (saved-toast-insight ds user-id saved?)
        settings (notif-domains/get-settings ds user-id)
        summary? (summary-showing? settings today today-entries)
        summary-insight (when summary? insight)]
    (html-response 200 (views/page request entries state-label insight
                                   {:toast-insight toast-insight
                                    :summary {:show summary?
                                              :csrf (get-in request [:anti-forgery-token])
                                              :state-label state-label
                                              :insight summary-insight}}))))