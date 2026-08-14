(ns app.views.entries
  (:require [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]))

(defn- field
  [label type name options]
  [:label {:class "form-control"}
   [:div {:class "label"}
    [:span {:class "label-text"} label]]
   (into [:input (merge {:type type
                         :name name
                         :class "input input-bordered w-full"} options)])])

(defn form
  []
  [:form {:hx-post "/entries"
          :hx-ext "json-enc"
          :hx-target "#form-error"
          :hx-swap "innerHTML"
          :_ "on htmx:afterRequest if event.detail.successful me.reset()"
          :class "card bg-base-200 p-4"}
   [:h2 {:class "text-lg font-semibold mb-3"} (i18n/t :entries/new-entry)]
   [:div {:class "grid grid-cols-1 gap-3 sm:grid-cols-2"}
    (field (i18n/t :entries/activity) "text" "activity" {:required true})
    (field (i18n/t :entries/effect) "text" "effect" {:required true})
    (field (i18n/t :entries/mood) "number" "mood_score" {:min 0 :max 10 :required true})
    (field (i18n/t :entries/sleep) "number" "sleep_hours" {:step "0.1"})]
   [:button {:type "submit" :class "btn btn-primary mt-4 w-full sm:w-auto"}
    (i18n/t :entries/add)]])

(defn item
  [{:keys [id date activity effect mood-score sleep-hours]}]
  [:li {:id (str "entry-" id)
        :hx-swap-oob (str "beforeend:#entries-list")
        :class "card bg-base-200 p-4"}
   [:div {:class "flex items-center justify-between gap-2 flex-wrap"}
    [:span {:class "text-lg font-semibold"}
     (i18n/t :entries/mood-label {:score mood-score})]
    [:span {:class "text-sm opacity-70"} (or date "")]]
   (when (seq activity)
     [:p {:class "mt-2"}
      [:span {:class "font-medium"} (i18n/t :entries/activity-label)]
      activity])
   (when (seq effect)
     [:p [:span {:class "font-medium"} (i18n/t :entries/effect-label)]
      effect])
   (when (some? sleep-hours)
     [:p [:span {:class "font-medium"} (i18n/t :entries/sleep-label)]
      (format "%.1f ч" (double sleep-hours))])])

(defn entries-list
  [entries]
  (if (seq entries)
    [:ul {:id "entries-list" :class "space-y-3"}
     (map item entries)]
    [:div
     [:ul {:id "entries-list" :class "space-y-3"}]
     [:p {:class "text-center opacity-60 py-8"} (i18n/t :entries/empty)]]))

(defn error-fragment
  [messages]
  [:div {:role "alert" :class "alert alert-error"}
   [:span (clojure.string/join "; " messages)]])

(defn page
  [request entries]
  (let [content [:div {:class "max-w-2xl mx-auto p-4"}
                 [:h1 {:class "text-2xl font-bold mb-1"} (i18n/t :entries/title)]
                 [:p {:class "text-sm opacity-70 mb-6"} (i18n/t :app/description)]
                 (form)
                 [:div {:id "form-error" :class "mt-3"}]
                 [:h2 {:class "text-xl font-semibold mt-8 mb-3"} (i18n/t :entries/list)]
                 (entries-list entries)]]
    (layout/layout {:title (i18n/t :entries/title)
                    :request request}
                   navigation/nav-items
                   content)))