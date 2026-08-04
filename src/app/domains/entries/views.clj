(ns app.domains.entries.views)

(def htmx-src "https://unpkg.com/htmx.org@2.0.10/dist/htmx.min.js")
(def json-enc-src "https://unpkg.com/htmx.org@2.0.10/dist/ext/json-enc.js")
(def hyperscript-src "https://unpkg.com/hyperscript.org@0.9.93/dist/_hyperscript.min.js")
(def tailwind-src "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4")
(def daisyui-href "https://cdn.jsdelivr.net/npm/daisyui@5.7.15/daisyui.css")

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
   [:h2 {:class "text-lg font-semibold mb-3"} "Новая запись"]
   [:div {:class "grid grid-cols-1 gap-3 sm:grid-cols-2"}
    (field "Активность" "text" "activity" {:required true})
    (field "Эффект" "text" "effect" {:required true})
    (field "Настроение (0–10)" "number" "mood_score" {:min 0 :max 10 :required true})
    (field "Сон (часы)" "number" "sleep_hours" {:step "0.1"})]
   [:button {:type "submit" :class "btn btn-primary mt-4 w-full sm:w-auto"}
    "Добавить запись"]])

(defn item
  [{:keys [id date activity effect mood-score sleep-hours]}]
  [:li {:id (str "entry-" id)
        :hx-swap-oob (str "beforeend:#entries-list")
        :class "card bg-base-200 p-4"}
   [:div {:class "flex items-center justify-between gap-2 flex-wrap"}
    [:span {:class "text-lg font-semibold"}
     "Настроение " mood-score "/10"]
    [:span {:class "text-sm opacity-70"} (or date "")]]
   (when (seq activity)
     [:p {:class "mt-2"}
      [:span {:class "font-medium"} "Активность: "]
      activity])
   (when (seq effect)
     [:p [:span {:class "font-medium"} "Эффект: "]
      effect])
   (when (some? sleep-hours)
     [:p [:span {:class "font-medium"} "Сон: "]
      (format "%.1f ч" (double sleep-hours))])])

(defn entries-list
  [entries]
  (if (seq entries)
    [:ul {:id "entries-list" :class "space-y-3"}
     (map item entries)]
    [:div
     [:ul {:id "entries-list" :class "space-y-3"}]
     [:p {:class "text-center opacity-60 py-8"} "Записей пока нет"]]))

(defn error-fragment
  [messages]
  [:div {:role "alert" :class "alert alert-error"}
   [:span (clojure.string/join "; " messages)]])

(defn page
  [entries]
  [:html {:lang "ru"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title "Дневник настроения"]
    [:link {:rel "stylesheet" :href daisyui-href}]
    [:script {:src tailwind-src}]
    [:script {:src htmx-src}]
    [:script {:src json-enc-src}]
    [:script {:src hyperscript-src}]]
   [:body {:data-theme "light" :class "bg-base-100 min-h-screen"}
    [:main {:class "max-w-2xl mx-auto p-4"}
     [:h1 {:class "text-2xl font-bold mb-1"} "Дневник настроения"]
     [:p {:class "text-sm opacity-70 mb-6"} "Трекер настроения при биполярном расстройстве"]
     (form)
     [:div {:id "form-error" :class "mt-3"}]
     [:h2 {:class "text-xl font-semibold mt-8 mb-3"} "Записи"]
     (entries-list entries)]]])
