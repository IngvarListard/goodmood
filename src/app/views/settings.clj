(ns app.views.settings
  (:require [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]))

(defn- language-switcher
  [csrf-token]
  [:div
   [:h3 {:class "font-semibold mb-2"} (i18n/t :user/language)]
   [:div {:class "flex gap-2"}
    (for [[locale code] [[:en (i18n/t :user/language-english)]
                         [:ru (i18n/t :user/language-russian)]]
          :let [current? (= i18n/*locale* locale)]]
      ^{:key locale}
      [:form {:method "post" :action "/locale"}
       [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
       [:input {:type "hidden" :name "locale" :value (name locale)}]
       [:button {:type "submit"
                 :class (str "btn btn-outline flex-1"
                             (when current? " btn-active"))}
        code]])]])

(defn- user-card
  [{:keys [display-name email] :as identity} request]
  (let [csrf-token (:anti-forgery-token request)]
    [:div {:class "card bg-base-200 p-4"}
     [:div {:class "flex items-center gap-3"}
      [:div {:class "avatar placeholder"}
       [:div {:class "bg-primary text-primary-content rounded-full w-12"}
        [:span {:class "text-lg font-bold"}
         (subs display-name 0 1)]]]
      [:div
       [:p {:class "font-semibold"} display-name]
       [:p {:class "text-sm opacity-70"} email]]]
     [:div {:class "divider"}]
     (language-switcher csrf-token)
     [:div {:class "divider"}]
     [:form {:method "post" :action "/logout"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
      [:button {:type "submit" :class "btn btn-error btn-outline w-full"}
       (i18n/t :auth/logout)]]]))

(defn page
  "Страница настроек: информация о пользователе, переключатель языка и выход.
   Служит точкой входа в меню учётной записи на мобильных устройствах."
  [request]
  (let [identity (:identity request)
        content [:div {:class "max-w-2xl mx-auto p-4"}
                 [:h1 {:class "text-2xl font-bold mb-4"} (i18n/t :nav/settings)]
                 (user-card identity request)]]
    (layout/layout {:title (i18n/t :nav/settings)
                    :active :settings
                    :request request}
                   navigation/nav-items
                   content)))