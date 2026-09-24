(ns app.views.settings
  (:require [app.i18n :as i18n]
            [app.views.ai :as ai]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]
            [app.views.notifications :as notifications]))

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

(defn- theme-switcher
  "Секция выбора темы: радио system/dark/light в join. Мгновенное превью —
   нативный механизм daisyUI theme-controller (:has); выбор сохраняется POST-ом
   на /theme. Форма с hx-boost=false: htmx-boost не свапает атрибуты <html>,
   тема меняется только полной перезагрузкой."
  [csrf-token theme]
  [:div
   [:h3 {:class "font-semibold mb-2"} (i18n/t :user/theme)]
   [:form {:method "post" :action "/theme"
           :hx-boost "false"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
    [:input {:type "hidden" :name "next" :value "/settings"}]
    [:div {:class "join flex w-full"}
     (for [[mode label] [[:system (i18n/t :user/theme-system)]
                         [:dark (i18n/t :user/theme-dark)]
                         [:light (i18n/t :user/theme-light)]]
           :let [current? (= theme mode)]]
       ^{:key mode}
       [:label {:class (str "btn btn-outline join-item flex-1"
                            (when current? " btn-active"))}
        [:input {:type "radio"
                 :name "theme"
                 :value (name mode)
                 :class "sr-only"
                 :checked current?
                 ;; Автосабмит формы при выборе радио (полный reload).
                 ;; Скобки вокруг query обязательны: possessive после
                 ;; <form/> без них не парсится (проверено в браузере).
                 :_ "on change call (the closest <form/>)'s submit()"}]
        label])]]])

(defn- user-card
  [{:keys [display-name email] :as identity} request]
  (let [csrf-token (:anti-forgery-token request)]
    [:div {:class "card bg-base-200 border border-base-300 p-4"}
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
     (theme-switcher csrf-token (:theme request))
     [:div {:class "divider"}]
     [:form {:method "post" :action "/logout"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
      [:button {:type "submit" :class "btn btn-error btn-outline w-full"}
       (i18n/t :auth/logout)]]]))

(defn push-section
  "Секция «Push-уведомления» (change add-pwa-push): статус и кнопки.
   csrf-token — токен для hx-headers кнопки тестовой отправки;
   configured? — VAPID-ключи на сервере; subscribed? — есть подписка в БД.
   Тексты и состояние рендерит сервер; push.js — тупой исполнитель (D6):
   читает data-атрибуты секции, возвращает свежий фрагмент после POST."
  [csrf-token configured? subscribed?]
  [:div {:id "push-section"
         :data-msg-denied (i18n/t :push/denied)
         :data-msg-error (i18n/t :push/error)
         :class "mb-6"}
   [:h2 {:class "text-sm font-medium text-base-content/60 mb-1 uppercase tracking-wide"}
    (i18n/t :push/title)]
   [:p {:class "text-xs text-base-content/60 mb-3"}
    (i18n/t :push/subtitle)]
   (if-not configured?
     [:div {:class "card bg-base-200 border border-base-300 p-4"}
      [:p {:class "text-sm opacity-70"} (i18n/t :push/unavailable)]]
     [:div {:class "card bg-base-200 border border-base-300 p-4"}
      (if subscribed?
        [:div
         [:div {:class "flex items-center justify-between gap-3"}
          [:span {:class "text-sm"} (i18n/t :push/status-enabled)]
          [:button {:type "button" :class "btn btn-outline btn-sm"
                    :_ "on click call window.GMPush.disable(me)"}
           (i18n/t :push/disable)]]
         [:div {:class "flex items-center gap-3 mt-3"}
          [:button {:type "button" :class "btn btn-outline btn-sm"
                    :hx-post "/push/test"
                    :hx-target "#push-test-result"
                    :hx-swap "innerHTML"
                    :hx-headers (str "{\"X-CSRF-Token\": \"" csrf-token "\"}")}
           (i18n/t :push/test-button)]
          [:span {:id "push-test-result" :class "text-sm opacity-70"}]]]
        [:button {:type "button"
                  :class "btn btn-primary w-full"
                  :_ "on click call window.GMPush.enable(me)"}
         (i18n/t :push/enable)])])])

(defn push-test-result
  "Статус тестовой отправки push: nil/0 доставок → ошибка, иначе число."
  [delivered]
  (if (and delivered (pos? delivered))
    [:span {:class "text-sm text-success"} (i18n/t :push/test-result {:count delivered})]
    [:span {:class "text-sm text-error"} (i18n/t :push/test-error)]))

(defn page
  "Страница настроек: информация о пользователе, переключатель языка, выход
   и секции настроек уведомлений, AI-помощника и push.
   request: ring-запрос; notif-slots: вектор строк из user_notification_settings;
   ai-settings: map настроек AI; push-state: {:configured? :subscribed?}."
  [request notif-slots & [ai-settings {:keys [configured? subscribed?] :as _push-state}]]
  (let [identity (:identity request)
        csrf (:anti-forgery-token request)
        content [:div {}
                 [:h1 {:class "text-2xl font-bold mb-4"} (i18n/t :nav/settings)]
                 (user-card identity request)
                 (when ai-settings (ai/ai-settings-section csrf ai-settings))
                 (notifications/settings-section csrf notif-slots)
                 (push-section csrf (boolean configured?) (boolean subscribed?))]]
    (layout/layout {:title (i18n/t :nav/settings)
                    :active :settings
                    :request request}
                   navigation/nav-items
                   content)))