(ns app.views.auth
  (:require [app.i18n :as i18n]
            [app.views.layout :as layout]))

(defn- language-switcher
  [csrf-token]
  [:div {:class "flex items-center justify-center gap-2 mt-6"}
   (for [[locale code] [[:en (i18n/t :user/language-english)]
                        [:ru (i18n/t :user/language-russian)]]
         :let [current? (= i18n/*locale* locale)]]
     ^{:key locale}
     [:form {:method "post" :action "/locale"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
      [:input {:type "hidden" :name "locale" :value (name locale)}]
      [:input {:type "hidden" :name "next" :value "/login"}]
      [:button {:type "submit"
                :class (str "btn btn-ghost btn-xs"
                            (when current? " btn-active"))}
       code]])])

(defn login-page
  "Render the login page. opts: optional :error i18n key to display,
   optional :next redirect target after successful login."
  [request {:keys [error next]}]
  (let [csrf-token (:anti-forgery-token request)]
    [:html {:lang (name i18n/*locale*)}
     (layout/head (i18n/t :auth/title) csrf-token)
     [:body {:data-theme "light"
             :class "bg-base-100 min-h-screen flex flex-col items-center justify-center p-4"}
      [:div {:class "card w-full max-w-sm bg-base-200 shadow-xl"}
       [:div {:class "card-body"}
        [:h1 {:class "card-title justify-center text-2xl"} (i18n/t :app/name)]
        [:p {:class "text-center text-sm opacity-70 mb-2"}
         (i18n/t :auth/title)]
        (when error
          [:div {:role "alert" :class "alert alert-error"}
           [:span (i18n/t error)]])
        [:form {:method "post" :action "/login"}
         [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
         (when next
           [:input {:type "hidden" :name "next" :value next}])
         [:label {:class "form-control"}
          [:div {:class "label"}
           [:span {:class "label-text"} (i18n/t :auth/email)]]
          [:input {:type "email"
                   :name "email"
                   :class "input input-bordered w-full"
                   :required true}]]
         [:label {:class "form-control mt-3"}
          [:div {:class "label"}
           [:span {:class "label-text"} (i18n/t :auth/password)]]
          [:input {:type "password"
                   :name "password"
                   :class "input input-bordered w-full"
                   :required true}]]
         [:button {:type "submit" :class "btn btn-primary w-full mt-6"}
          (i18n/t :auth/login)]]]]
      (language-switcher csrf-token)]]))