(ns app.views.user-menu
  (:require [app.i18n :as i18n]))

(defn language-link
  "Кнопка переключения языка, отправляющая POST на /locale."
  [locale code csrf-token]
  (let [current? (= i18n/*locale* locale)]
    [:form {:method "post" :action "/locale"}
     [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
     [:input {:type "hidden" :name "locale" :value (name locale)}]
     [:button {:type "submit"
               :class (str "btn btn-ghost btn-sm justify-start w-full"
                           (when current? " btn-active"))}
      code]]))

(defn user-menu
  "Чип пользователя с выпадающим меню (выход, переключатель языка).
   Рендерится внизу боковой панели на десктопе.
   identity: map аутентифицированного пользователя; request: ring-запрос (для CSRF-токена)."
  [{:keys [display-name] :as identity} request]
  (let [csrf-token (:anti-forgery-token request)]
    [:div {:class "dropdown dropdown-top w-full p-3 border-t border-base-200"}
     [:div {:tabindex 0
            :role "button"
            :class "btn btn-ghost flex items-center gap-2 w-full justify-start normal-case"}
      [:div {:class "avatar placeholder"}
       [:div {:class "bg-primary text-primary-content rounded-full w-8"}
        [:span {:class "text-sm font-bold"}
         (subs display-name 0 1)]]]
      [:span {:class "truncate"} display-name]]
     [:ul {:tabindex 0
           :class "dropdown-content menu bg-base-100 rounded-box z-50 w-52 p-2 shadow"}
      [:li
       [:form {:method "post" :action "/logout"}
        [:input {:type "hidden"
                 :name "__anti-forgery-token"
                 :value csrf-token}]
        [:button {:type "submit" :class "w-full text-left"}
         (i18n/t :auth/logout)]]]
      [:li {:class "menu-title"}
       (i18n/t :user/language)]
      [:li
       [:div {:class "flex flex-col gap-1 w-full px-2 py-1"}
        (language-link :en (i18n/t :user/language-english) csrf-token)
        (language-link :ru (i18n/t :user/language-russian) csrf-token)]]]]))