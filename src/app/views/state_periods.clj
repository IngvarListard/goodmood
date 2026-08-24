(ns app.views.state-periods
  (:require [app.i18n :as i18n]
            [cheshire.core :as json]))

(defn- format-date
  "Дата «YYYY-MM-DD» из SQLite datetime (обрезаем время)."
  [date]
  (when date
    (let [s (str date)]
      (if (>= (count s) 10) (subs s 0 10) s))))

(defn- start-period-modal
  "Модалка начала периода: пресеты ярлыков (быстрый старт) + свободный ввод
   label/notes. Прессеты сразу POST'ят /periods/start с нужным label."
  [csrf-token]
  (let [presets [{:key "low"     :label (i18n/t :periods/preset-low)}
                 {:key "high"    :label (i18n/t :periods/preset-high)}
                 {:key "anxious" :label (i18n/t :periods/preset-anxious)}]]
    [:dialog {:id "period-start-modal" :class "modal modal-bottom sm:modal-middle"}
     [:div {:class "modal-box"}
      [:h3 {:class "text-lg font-bold mb-2"} (i18n/t :periods/start-title)]
      [:div {:class "form-control mb-3"}
       [:label {:class "label"}
        [:span {:class "label-text"} (i18n/t :periods/presets)]]
       [:div {:class "flex flex-wrap gap-2"}
        (for [{:keys [key label]} presets]
          ^{:key key}
          [:button {:type "button"
                    :class "btn btn-outline btn-sm"
                    :hx-post "/periods/start"
                    :hx-ext "json-enc"
                    :hx-target "#period-indicator"
                    :hx-swap "outerHTML"
                    :hx-vals (json/generate-string
                              {"__anti-forgery-token" csrf-token
                               "label" label})}
           label])]]
      [:form {:hx-post "/periods/start"
              :hx-ext "json-enc"
              :hx-target "#period-indicator"
              :hx-swap "outerHTML"
              :class "space-y-3"}
       [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
       [:div {:class "form-control"}
        [:label {:class "label" :for "period-label"}
         [:span {:class "label-text"} (i18n/t :periods/label)]]
        [:input {:id "period-label" :name "label" :type "text" :required true
                 :class "input input-bordered w-full"
                 :placeholder (i18n/t :periods/label-placeholder)}]]
       [:div {:class "form-control"}
        [:label {:class "label" :for "period-notes"}
         [:span {:class "label-text"} (i18n/t :periods/notes)]]
        [:textarea {:id "period-notes" :name "notes" :rows "2"
                    :class "textarea textarea-bordered w-full"
                    :placeholder (i18n/t :periods/notes-placeholder)}]]
       [:div {:class "modal-action"}
        [:button {:type "button"
                  :class "btn btn-ghost"
                  :_ "on click call #period-start-modal.close()"}
         (i18n/t :periods/cancel)]
        [:button {:type "submit" :class "btn btn-primary"}
         (i18n/t :periods/start)]]]]]))

(defn start-banner
  "Баннер «Начать период» (когда активного периода нет): мягкий hint + кнопка
   открытия модал. Заменяет #period-indicator при старте периода."
  [csrf-token]
  [:div {:id "period-indicator" :data-testid "period-start"}
   [:div {:class "alert alert-warning shadow-sm"}
    [:div {:class "flex items-start gap-3 w-full"}
     [:p {:class "text-sm opacity-90"} (i18n/t :periods/hint)]
     [:button {:type "button"
               :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
               :_ "on click call #period-start-modal.showModal()"}
      (i18n/t :periods/start)]]
    (start-period-modal csrf-token)]])

(defn active-indicator
  "Индикатор активного периода + кнопка «закрыть». Заменяет #period-indicator
   при закрытии периода."
  [csrf-token period]
  [:div {:id "period-indicator" :data-testid "active-period-indicator"}
   [:div {:class "alert alert-primary shadow-sm"}
    [:div {:class "flex items-center gap-3 w-full"}
     [:div {:class "flex-1 min-w-0"}
      [:p {:class "text-sm font-medium"} (i18n/t :periods/active)]
      [:p {:class "text-sm opacity-80"}
       (str (:label period) " · " (format-date (:started-at period)))]]
     [:button {:type "button"
               :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
               :hx-post (str "/periods/" (:id period) "/end")
               :hx-ext "json-enc"
               :hx-target "#period-indicator"
               :hx-swap "outerHTML"
               :hx-vals (str "{\"__anti-forgery-token\": \"" csrf-token "\"}")}
      (i18n/t :periods/close)]]]])

(defn period-list
  "Список периодов пользователя (ретроспектива, read-only). Пусто — nil."
  [periods]
  (when (seq periods)
    [:div {:class "card bg-base-200 shadow-sm mb-4" :id "periods-list"
           :data-testid "periods-list"}
     [:div {:class "card-body p-4"}
      [:h2 {:class "text-sm font-medium opacity-60 mb-2 uppercase tracking-wide"}
       (i18n/t :periods/list-title)]
      (for [p periods]
        ^{:key (:id p)}
        [:p {:class "text-sm mb-1"}
         (str (:label p) " — " (format-date (:started-at p))
              (if-let [ended (:ended-at p)]
                (str " → " (format-date ended))
                " → …"))])]]))

(defn validation-error
  "Фрагмент ошибки валидации при старте периода."
  []
  [:div {:class "alert alert-warning shadow-sm"}
   [:span {:class "text-sm"} (i18n/t :periods/error)]])