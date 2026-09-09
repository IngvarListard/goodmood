(ns app.views.navigation
  (:require [app.i18n :as i18n]
            [app.icons :as icons]))

(def nav-items
  [{:id :feed :label-key :nav/feed :icon "list-bullet" :route "/feed"}
   {:id :insights :label-key :nav/insights :icon "light-bulb" :route "/insights"}
   {:id :check-in :label-key :nav/check-in :icon "plus-circle" :route "/check-in"}
   {:id :medications :label-key :nav/medications :icon "beaker" :route "/medications"}
   {:id :settings :label-key :nav/settings :icon "cog-6-tooth" :route "/settings"}])

(defn- nav-link
  [{:keys [id label-key icon route]} active? variant]
  (let [label (i18n/t label-key)
        icon-variant (if active? :solid :outline)
        layout-classes (case variant
                         :mobile "flex-col items-center justify-center gap-0.5 h-full min-h-[44px]"
                         :desktop "items-center gap-2")
        ;; цвет ссылки: активный — primary; неактивный (mobile) — muted + hover
        color-classes (case variant
                        :desktop (when active? "menu-active text-primary")
                        :mobile (if active?
                                  "text-primary"
                                  "text-base-content/60 hover:text-base-content"))
        link-classes (str "flex w-full rounded-lg transition-colors relative "
                          layout-classes
                          (when color-classes (str " " color-classes)))
        ;; контейнер иконки (mobile): у активного — плашка bg-primary/10, у неактивных — только размер для выравнивания
        icon-box-classes (when (= variant :mobile)
                           (if active?
                             "flex h-9 w-9 items-center justify-center rounded-xl bg-primary/10"
                             "flex h-9 w-9 items-center justify-center"))
        icon-el (icons/svg icon {:variant icon-variant})]
    [:li {:class (when (= variant :mobile) "flex-1")}
     [:a {:href route
          :class link-classes
          :aria-current (when active? "page")}
      (if icon-box-classes
        [:span {:class icon-box-classes} icon-el]
        icon-el)
      [:span {:class "text-xs"} label]
      (when (and active? (= variant :mobile))
        [:span {:class "absolute bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-primary"}])]]))

(defn navigation
  "Отрендерить компонент навигации.
   variant: :mobile (горизонтальная нижняя панель) или :desktop (вертикальная боковая панель)
   items: вектор пунктов навигации
   opts: опциональная map с ключом :active (id пункта)"
  [variant items & [{:keys [active]}]]
  (case variant
    :mobile
    [:nav {:class "flex items-stretch"}
     [:ul {:class "menu menu-horizontal flex-nowrap gap-1 flex-1 w-full h-[88px] px-2"}
      (doall
       (for [item items]
         ^{:key (:id item)}
         (nav-link item (= active (:id item)) :mobile)))]]

    :desktop
    [:nav {:class "flex flex-col h-full"}
     [:div {:class "p-4"}
      [:h1 {:class "text-xl font-bold"} (i18n/t :app/name)]]
     [:ul {:class "menu menu-vertical gap-1 flex-1 w-full px-2"}
      (doall
       (for [{:keys [id] :as item} items]
         ^{:key id}
         (nav-link item (= active id) :desktop)))]]))

(defn nav-label
  "Перевести название пункта навигации для текущей локали."
  [item]
  (i18n/t (:label-key item)))