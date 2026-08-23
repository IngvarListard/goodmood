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

(defn- mood-range
  "Слайдер настроения (mood_score) — ядро, видим всегда, в т.ч. в мягком режиме."
  []
  (range-field (i18n/t :entries/mood) "mood" "mood_score" "range-primary"))

(defn- soft-fields
  "Энергия/тревога + опциональные блоки. Оборачиваются в #soft-targets,
   чтобы мягкий режим мог скрыть их одним toggle (Decision 14.4)."
  []
  [:div {:id "soft-targets"}
   (range-field (i18n/t :entries/energy) "energy" "energy" "range-success")
   (range-field (i18n/t :entries/anxiety) "anxiety" "anxiety" "range-warning")
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
                             :class "input input-bordered w-full"}])]])

(defn- soft-mode-banner
  "Баннер предложения мягкого режима (low/mixed). Non-blocking: полная форма
   остаётся доступной, toggle — hyperscript, без сырого JS."
  []
  [:div {:class "alert alert-info shadow-sm mb-4" :id "soft-mode-banner"}
   [:div {:class "flex items-start gap-3 w-full"}
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :width 20 :height 20 :viewBox "0 0 24 24"
           :fill "none" :stroke "currentColor"
           :stroke-width 1.8 :stroke-linecap "round" :stroke-linejoin "round"}
     [:path {:d "M12 18v-5.25m0 0a6.01 6.01 0 0 0 1.5-.189m-1.5.189a6.01 6.01 0 0 1-1.5-.189m3.75 7.429a3.75 3.75 0 1 1-7.5 0V8.25M8.25 8.25a3.75 3.75 0 0 1 7.5 0"}]
     [:path {:d "M9 18h6"}]
     [:path {:d "M10 21h4"}]]
    [:div {:class "flex-1 min-w-0"}
     [:p {:class "text-sm font-medium"}
      (i18n/t :check-in/soft-mode-title)]
     [:p {:class "text-sm opacity-80 mt-1"}
      (i18n/t :check-in/soft-mode-desc)]
     [:div {:class "flex gap-2 mt-2 flex-wrap"}
      [:button {:type "button"
                :id "soft-mode-btn"
                :class "btn btn-primary btn-sm h-11 min-h-11 px-4"
                :_ "on click add .hidden to #soft-targets"}
       (i18n/t :check-in/soft-mode-accept)]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-4"
                :_ "on click remove .hidden from #soft-targets"}
       (i18n/t :check-in/soft-mode-decline)]]]]])

(defn form
  "Сформировать HTMX-форму создания записи. При успехе — редирект на /feed?saved=1.
   soft? — показывать баннер мягкого режима (последняя запись low/mixed)."
  [csrf-token soft?]
  [:div
   (when soft?
     (soft-mode-banner))
   [:form {:hx-post "/entries"
           :hx-ext "json-enc"
           :hx-target "#form-error"
           :hx-swap "innerHTML"
           :_ "on htmx:afterRequest
                 if event.detail.successful
                   set window.location to '/feed?saved=1'
                 end"
           :class "card bg-base-200 p-4"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
    (template-tabs)
    [:div {:id "form-error" :class "mb-3"}]
    (mood-range)
    (soft-fields)
    [:button {:type "submit" :class "btn btn-primary w-full h-12 mt-4"}
     (i18n/t :entries/save)]]])

(defn page
  "Отрендерить страницу /check-in: «← назад» на /feed, заголовок и форма создания
   записи. soft? — показывать мягкий режим (последняя запись low/mixed)."
  [request soft?]
  (let [content [:div {:class "max-w-2xl mx-auto p-4 pb-24"}
                 [:div {:class "flex items-center gap-3 mb-6"}
                  [:a {:href "/feed"
                       :class "btn btn-ghost btn-circle btn-sm"
                       :aria-label (i18n/t :check-in/back)}
                   [:svg {:xmlns "http://www.w3.org/2000/svg"
                          :width 20 :height 20 :viewBox "0 0 24 24"
                          :fill "none" :stroke "currentColor"
                          :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
                    [:path {:d "M15 18l-6-6 6-6"}]]]
                  [:h1 {:class "text-2xl font-bold"} (i18n/t :check-in/title)]]
                 (form (:anti-forgery-token request) soft?)]]
    (layout/layout {:title (i18n/t :check-in/title)
                    :active :check-in
                    :request request}
                   navigation/nav-items
                   content)))