(ns app.views.navigation
  (:require [app.icons :as icons]))

(def nav-items
  [{:id :dashboard :label "Дашборд" :icon "home" :route "/dashboard"}
   {:id :check-in :label "Чек-ин" :icon "plus-circle" :route "/check-in"}
   {:id :history :label "История" :icon "clock" :route "/history"}
   {:id :statistics :label "Статистика" :icon "chart-bar" :route "/statistics"}
   {:id :insights :label "Инсайты" :icon "light-bulb" :route "/insights"}
   {:id :settings :label "Настройки" :icon "cog-6-tooth" :route "/settings"}])

(defn- nav-link
  [{:keys [id icon label route]} active? variant]
  (let [icon-variant (if active? :solid :outline)
        layout-classes (case variant
                         :mobile "flex-col items-center justify-center gap-0.5 py-2"
                         :desktop "items-center gap-2")
        active-classes (when active?
                         (case variant
                           :mobile "text-primary"
                           :desktop "menu-active text-primary"))
        link-classes (str "flex w-full rounded-lg transition-colors "
                          layout-classes
                          (when active-classes (str " " active-classes)))]
    [:li {:class (when (= variant :mobile) "flex-1")}
     [:a {:href route
          :class link-classes
          :aria-current (when active? "page")}
      (icons/svg icon {:variant icon-variant})
      [:span {:class "text-xs"} label]]]))

(defn navigation
  "Render navigation component.
   variant: :mobile (horizontal bottom bar) or :desktop (vertical sidebar)
   items: vector of nav items
   opts: optional map with :active key (item id)"
  [variant items & [{:keys [active]}]]
  (case variant
    :mobile
    [:nav {:class "flex items-stretch"}
     [:ul {:class "menu menu-horizontal gap-1 flex-1 w-full px-2"}
      (doall
       (for [item items]
         ^{:key (:id item)}
         (nav-link item (= active (:id item)) :mobile)))]]

    :desktop
    [:nav {:class "flex flex-col h-full"}
     [:div {:class "p-4"}
      [:h1 {:class "text-xl font-bold"} "Good Mood"]]
     [:ul {:class "menu menu-vertical gap-1 flex-1 w-full px-2"}
      (doall
       (for [{:keys [id] :as item} items]
         ^{:key id}
         (nav-link item (= active id) :desktop)))]]))