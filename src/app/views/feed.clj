(ns app.views.feed
  (:require [app.domains.entries :as domains]
            [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]
            [app.views.rose :as rose]
            [clojure.string :as str]))

(defn- entry-label
  "Вернуть локализованный ярлык состояния записи.
   Если state_label задан — использовать его (override), иначе вычислить rule-based."
  [entry]
  (let [label-kw (or (some-> (:state-label entry) keyword)
                     (domains/state-label entry))]
    (i18n/t label-kw)))

(defn- format-time
  "Извлечь время «HH:MM» из created_at (формат SQLite datetime('now'))."
  [created-at]
  (when created-at
    (let [parts (str/split created-at #"[ T]")]
      (when (>= (count parts) 2)
        (subs (second parts) 0 5)))))

(defn- month-name
  "Русское название месяца по номеру (1-12) через i18n."
  [month-num]
  (i18n/t (keyword (str "feed/month-" month-num))))

(defn- day-header
  "Локализованный заголовок дня: сегодня / вчера / «21 августа»."
  [date-str]
  (let [today (java.time.LocalDate/now)
        date (java.time.LocalDate/parse date-str)]
    (cond
      (= date today) (i18n/t :feed/today)
      (= date (.minusDays today 1)) (i18n/t :feed/yesterday)
      :else (str (.getDayOfMonth date) " " (month-name (.getMonthValue date))))))

(defn- axes-line
  "Строка «энергия 4 · тревога 7 · фокус 3» для карточек."
  [{:keys [energy anxiety focus]}]
  (str (i18n/t :entries/energy) " " (or energy "-")
       " · " (i18n/t :entries/anxiety) " " (or anxiety "-")
       " · " (i18n/t :entries/focus) " " (or focus "-")))

(defn- compact-card
  "Компактная карточка записи: бейдж ярлыка, timestamp, значения осей."
  [{:keys [created-at] :as entry}]
  [:li [:div {:class "card bg-base-200 shadow-sm"}
        [:div {:class "card-body p-3"}
         [:div {:class "flex items-center gap-2 mb-1 flex-wrap"}
          [:span {:class "text-xs uppercase tracking-wide opacity-50"}
           (i18n/t :feed/state-label)]
          [:span {:class "badge badge-secondary badge-sm"} (entry-label entry)]
          [:span {:class "text-xs opacity-70 tabular-nums ml-auto"}
           (format-time created-at)]]
         [:p {:class "text-sm opacity-85"} (axes-line entry)]]]])

(defn- hero-card
  "Hero-карточка последней записи сегодня: SVG-радар 200×200 + метаданные."
  [{:keys [sleep-hours note created-at] :as entry}]
  [:article {:class "card bg-base-200 shadow-md"}
   [:div {:class "card-body p-4"}
    [:div {:class "flex flex-col sm:flex-row gap-4 items-start"}
     [:div {:class "shrink-0 mx-auto"}
      (rose/radar (select-keys entry [:energy :anxiety :focus :mood-score]))]
     [:div {:class "flex-1 min-w-0 w-full"}
      [:div {:class "flex items-center gap-2 mb-1 flex-wrap"}
       [:span {:class "text-xs uppercase tracking-wide opacity-50"}
        (i18n/t :feed/state-label)]
       [:span {:class "badge badge-secondary badge-lg"} (entry-label entry)]
       [:span {:class "text-sm opacity-70 tabular-nums ml-auto"}
        (format-time created-at)]]
      [:p {:class "text-sm opacity-85"} (axes-line entry)]
      (when sleep-hours
        [:p {:class "text-sm opacity-60 mt-1"}
         (str (i18n/t :entries/sleep) " "
              (format "%.1f" (double sleep-hours)) " ч")])
      (when (seq note)
        [:p {:class "text-sm opacity-80 mt-2"} note])]]]])

(defn- today-section
  "Секция «Сегодня»: hero-карточка последней записи + компактные остальные,
   или онбординг при отсутствии записей."
  [today-entries]
  [:section {:class "mb-6"}
   [:h2 {:class "text-sm font-medium opacity-60 mb-2 uppercase tracking-wide"}
    (i18n/t :feed/today)]
   (if (seq today-entries)
     (let [latest (first today-entries)
           rest-entries (rest today-entries)]
       [:div
        (hero-card latest)
        (when (seq rest-entries)
          [:ul {:class "space-y-2 mt-2"}
           (map compact-card rest-entries)])])
     [:div {:class "text-center py-10"}
      [:p {:class "opacity-70 mb-4"} (i18n/t :feed/empty)]
      [:a {:href "/check-in" :class "btn btn-primary"}
       (i18n/t :feed/go-check-in)]])])

(defn- past-day-section
  "Секция прошлого дня: заголовок даты + компактные карточки."
  [date entries]
  [:section {:class "mb-6"}
   [:h2 {:class "text-sm font-medium opacity-60 mb-2 uppercase tracking-wide"}
    (day-header date)]
   [:ul {:class "space-y-2"}
    (map compact-card entries)]])

(defn- fab
  "Единственная плавающая кнопка «+» (правый нижний угол, над мобильной навигацией)."
  []
  [:a {:href "/check-in"
       :class "btn btn-primary btn-circle fixed bottom-20 right-4 shadow-lg z-50"
       :aria-label (i18n/t :feed/new-entry)}
   [:svg {:xmlns "http://www.w3.org/2000/svg"
          :width 24 :height 24 :viewBox "0 0 24 24"
          :fill "none" :stroke "currentColor"
          :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
    [:path {:d "M12 5v14M5 12h14"}]]])

(defn page
  "Отрендерить страницу /feed: лента записей, сгруппированных по дням.
   Последняя запись сегодня — hero-карточка с розой; остальные — компактные.
   request: ring-запрос; entries: вектор записей (date desc, created_at desc)."
  [request entries]
  (let [grouped (group-by :date entries)
        today (str (java.time.LocalDate/now))
        today-entries (get grouped today)
        past-dates (remove #{today} (keys grouped))
        content [:div {:class "max-w-2xl mx-auto p-4 pb-24"}
                 [:div {:class "mb-6"}
                  [:h1 {:class "text-2xl font-bold"} (i18n/t :feed/title)]]
                 (today-section today-entries)
                 (for [date past-dates]
                   (past-day-section date (get grouped date)))
                 (fab)]]
    (layout/layout {:title (i18n/t :feed/title)
                    :active :feed
                    :request request}
                   navigation/nav-items
                   content)))