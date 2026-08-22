(ns app.views.entries
  (:require [app.i18n :as i18n]))

(defn item
  "Отрендерить одну карточку записи (HTML-фрагмент для htmx-ответа POST /entries).
   Ожидает map с ключами :id, :date, :mood-score, :energy, :anxiety, :sleep-hours,
   :note, :activity."
  [{:keys [id date mood-score energy anxiety sleep-hours note activity]}]
  [:li {:id (str "entry-" id)
        :hx-swap-oob (str "afterbegin:#entries-list")
        :class "card bg-base-200 p-4"}
   [:div {:class "flex items-center justify-between gap-2 flex-wrap"}
    [:span {:class "text-lg font-semibold"}
     (i18n/t :entries/mood-label {:score (or mood-score "-")})]
    [:span {:class "text-sm opacity-70"} (or date "")]]
   [:div {:class "flex flex-wrap gap-3 mt-2 text-sm opacity-80"}
    (when (some? energy)
      [:span (i18n/t :entries/energy-label) (str energy "/10")])
    (when (some? anxiety)
      [:span (i18n/t :entries/anxiety-label) (str anxiety "/10")])]
   (when (some? sleep-hours)
     [:p [:span {:class "font-medium"} (i18n/t :entries/sleep-label)]
      (format "%.1f ч" (double sleep-hours))])
   (when (seq note)
    [:p {:class "mt-2"} note])
   (when (seq activity)
    [:p {:class "mt-2"}
     [:span {:class "font-medium"} (i18n/t :entries/activity-label)]
     activity])])

(defn error-fragment
  "Мягкое сообщение об ошибке валидации (alert-warning, не alert-error)."
  [messages]
  [:div {:role "alert" :class "alert alert-warning"}
   [:span (clojure.string/join "; " messages)]])