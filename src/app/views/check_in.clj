(ns app.views.check-in
  (:require [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]))

(defn- value-row
  "Отрендерить подпись значения слайдера: минимум, текущее значение, максимум."
  [value-id]
  [:div {:class "flex justify-between mt-0.5 px-0.5"}
   [:span {:class "text-xs opacity-40"} "0"]
   [:span {:id value-id :class "text-sm font-semibold tabular-nums"} "5"]
   [:span {:class "text-xs opacity-40"} "10"]])

(defn- range-field
  "Отрендерить range-поле ядра: label, слайдер с отображением текущего значения.
   field-id: суффикс id для value-спана, name: имя поля формы."
  [label field-id name color]
  [:div {:class "form-control mb-5"}
   [:div {:class "label px-0"}
    [:span {:class "label-text text-base font-medium"} label]]
   [:input {:type "range" :name name :min "0" :max "10" :value "5"
            :class (str "range " color)
            :style "height: 2rem"
            :_ (str "on input put my value into #" field-id "-value")}]
   (value-row (str field-id "-value"))])

(defn- template-tabs
  "Отрендерить переключатель шаблонов (утро/день/вечер/событие)."
  []
  [:div {:class "tabs tabs-boxed mb-4"}
   (map (fn [{:keys [key label active]}]
          [:button {:type "button"
                    :class (str "tab" (when active " tab-active"))
                    :data-template key
                    :role "tab"
                    :_ "on click
                          remove .tab-active from .tab
                          add .tab-active to me
                          set #template-value.value to @data-template"}
           label])
        [{:key "morning" :label (i18n/t :template/morning) :active true}
         {:key "day" :label (i18n/t :template/day)}
         {:key "evening" :label (i18n/t :template/evening)}
         {:key "event" :label (i18n/t :template/event)}])
   [:input {:type "hidden" :name "template" :id "template-value" :value "morning"}]])

(defn- optional-block
  "Отрендерить опциональный коллапс-блок: чекбокс-переключатель, заголовок и контент."
  [label field-id content]
  [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
   [:input {:type "checkbox"
            :_ (str "on change
                       if me.checked
                         remove [disabled] from #" field-id "
                       else
                         add [disabled] to #" field-id)}]
   [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"} label]
   [:div {:class "collapse-content"} content]])

(defn form
  "Сформировать HTMX-форму создания записи. При успехе — редирект на /feed."
  [csrf-token]
  [:form {:hx-post "/entries"
          :hx-ext "json-enc"
          :hx-target "#form-error"
          :hx-swap "innerHTML"
          :_ "on htmx:afterRequest
                if event.detail.successful
                  set window.location to '/feed'
                end"
          :class "card bg-base-200 p-4"}
   [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
   (template-tabs)
   [:div {:id "form-error" :class "mb-3"}]
   [:div {:class "grid grid-cols-1 gap-3"}
    (range-field (i18n/t :entries/mood) "mood" "mood_score" "range-primary")
    (range-field (i18n/t :entries/energy) "energy" "energy" "range-success")
    (range-field (i18n/t :entries/anxiety) "anxiety" "anxiety" "range-warning")]
   [:div {:class "border-t border-base-300 pt-4 mt-2"}
    [:p {:class "text-xs opacity-50 mb-3 tracking-wide uppercase"}
     (i18n/t :entries/optional)]
    (optional-block (i18n/t :entries/focus) "focus"
     [:div {:class "form-control"}
      [:input {:type "range" :name "focus" :min "0" :max "10" :value "5"
               :id "focus" :data-optional true :disabled true
               :class "range range-info" :style "height: 2rem"
               :_ "on input set #focus-value.textContent to my.value"}]
      (value-row "focus-value")])
    (optional-block (i18n/t :entries/sleep) "sleep_hours"
     [:input {:type "number" :name "sleep_hours" :id "sleep_hours"
              :data-optional true :disabled true
              :step "0.1" :min "0" :max "24"
              :placeholder "7.5"
              :class "input input-bordered w-full"}])
    (optional-block (i18n/t :entries/note) "note"
     [:textarea {:name "note" :id "note" :rows "3" :maxlength "500"
                 :data-optional true :disabled true
                 :placeholder "..."
                 :class "textarea textarea-bordered w-full"}])
    (optional-block (i18n/t :entries/activity) "activity"
     [:input {:type "text" :name "activity" :id "activity"
              :data-optional true :disabled true
              :placeholder "..."
              :class "input input-bordered w-full"}])]
   [:button {:type "submit" :class "btn btn-primary w-full h-12 mt-4"}
    (i18n/t :entries/save)]])

(defn page
  "Отрендерить страницу /check-in: «← назад» на /feed, заголовок и форма создания записи."
  [request]
  (let [content [:div {:class "max-w-2xl mx-auto p-4 pb-24"}
                 [:div {:class "flex items-center gap-3 mb-6"}
                  [:a {:href "/feed"
                       :class "btn btn-ghost btn-circle btn-sm"
                       :aria-label (i18n/t :check-in/back)}
                   [:svg {:xmlns "http://www.w3.org/2000/svg"
                          :width 20 :height 20 :viewBox "0 0 24 24"
                          :fill "none" :stroke "currentColor"
                          :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
                    [:path {:d "M15 18l-6-6 6-6"}]]]
                  [:h1 {:class "text-2xl font-bold"} (i18n/t :check-in/title)]]
                 (form (:anti-forgery-token request))]]
    (layout/layout {:title (i18n/t :check-in/title)
                    :active :check-in
                    :request request}
                   navigation/nav-items
                   content)))