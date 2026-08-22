(ns preview
  "Рендерит hiccup-вектор в HTML-файл для предпросмотра в браузере.

  Использование:
    1. Вставь свой hiccup-вектор в body (ниже, помечено PLACEHOLDER).
    2. Запусти: clj -M -i dev/preview.clj
    3. Открой в браузере: resources/proto/preview.html

  DaisyUI/Tailwind подключены через CDN, тёмная тема по умолчанию."
  (:require [hiccup2.core :refer [html]]))

;; === PLACEHOLDER: вставь свой hiccup сюда ===
(def body
   [:div {:class "max-w-2xl mx-auto p-4 pb-24"}
 ;; ── Шапка: назад + заголовок ───────────────────────────
 [:div {:class "flex items-center gap-3 mb-6"}
  [:a {:href "/feed"
       :class "btn btn-ghost btn-circle btn-sm"
       :aria-label "Назад к ленте"}
   [:svg {:xmlns "http://www.w3.org/2000/svg"
          :width 20 :height 20 :viewBox "0 0 24 24"
          :fill "none" :stroke "currentColor"
          :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
    [:path {:d "M15 18l-6-6 6-6"}]]]
  [:h1 {:class "text-2xl font-bold"} "Новая запись"]]

 ;; ── Форма (существующая) ────────────────────────────────
 ;; csrf-token передаётся рантаймом, здесь — плейсхолдер.
 [:form {:hx-post "/entries"
         :hx-ext "json-enc"
         :hx-target "#form-error"
         :hx-swap "innerHTML"
         :hx-disabled-elt "this button[type=submit]"
         :_ "on htmx:afterRequest
               if event.detail.successful
                 set window.location to '/feed'
               end"
         :class "card bg-base-200 p-4"}
  [:input {:type "hidden" :name "__anti-forgery-token" :value "{{csrf-token}}"}]

  ;; template-tabs (утро/день/вечер/событие)
  [:div {:class "tabs tabs-boxed mb-4"}
   [:button {:type "button"
             :class "tab tab-active"
             :data-template "morning"
             :role "tab"
             :_ "on click
                   remove .tab-active from .tab
                   add .tab-active to me
                   set #template-value.value to @data-template"}
    "Утро"]
   [:button {:type "button"
             :class "tab"
             :data-template "day"
             :role "tab"
             :_ "on click
                   remove .tab-active from .tab
                   add .tab-active to me
                   set #template-value.value to @data-template"}
    "День"]
   [:button {:type "button"
             :class "tab"
             :data-template "evening"
             :role "tab"
             :_ "on click
                   remove .tab-active from .tab
                   add .tab-active to me
                   set #template-value.value to @data-template"}
    "Вечер"]
   [:button {:type "button"
             :class "tab"
             :data-template "event"
             :role "tab"
             :_ "on click
                   remove .tab-active from .tab
                   add .tab-active to me
                   set #template-value.value to @data-template"}
    "Событие"]
   [:input {:type "hidden" :name "template" :id "template-value" :value "morning"}]]

  ;; контейнер ошибок
  [:div {:id "form-error" :class "mb-3"}]

  ;; ядро: mood_score / energy / anxiety (range 0-10)
  [:div {:class "grid grid-cols-1 gap-3"}
   [:div {:class "form-control mb-5"}
    [:div {:class "label px-0"}
     [:span {:class "label-text text-base font-medium"} "Настроение"]]
    [:input {:type "range" :name "mood_score" :min "0" :max "10" :value "5"
             :class "range range-primary" :style "height: 2rem"
             :_ "on input put my value into #mood-value"}]
    [:div {:class "flex justify-between mt-0.5 px-0.5"}
     [:span {:class "text-xs opacity-40"} "0"]
     [:span {:id "mood-value" :class "text-sm font-semibold tabular-nums"} "5"]
     [:span {:class "text-xs opacity-40"} "10"]]]

   [:div {:class "form-control mb-5"}
    [:div {:class "label px-0"}
     [:span {:class "label-text text-base font-medium"} "Энергия"]]
    [:input {:type "range" :name "energy" :min "0" :max "10" :value "5"
             :class "range range-success" :style "height: 2rem"
             :_ "on input put my value into #energy-value"}]
    [:div {:class "flex justify-between mt-0.5 px-0.5"}
     [:span {:class "text-xs opacity-40"} "0"]
     [:span {:id "energy-value" :class "text-sm font-semibold tabular-nums"} "5"]
     [:span {:class "text-xs opacity-40"} "10"]]]

   [:div {:class "form-control mb-5"}
    [:div {:class "label px-0"}
     [:span {:class "label-text text-base font-medium"} "Тревога"]]
    [:input {:type "range" :name "anxiety" :min "0" :max "10" :value "5"
             :class "range range-warning" :style "height: 2rem"
             :_ "on input put my value into #anxiety-value"}]
    [:div {:class "flex justify-between mt-0.5 px-0.5"}
     [:span {:class "text-xs opacity-40"} "0"]
     [:span {:id "anxiety-value" :class "text-sm font-semibold tabular-nums"} "5"]
     [:span {:class "text-xs opacity-40"} "10"]]]]

  ;; опциональные блоки (collapse)
  [:div {:class "border-t border-base-300 pt-4 mt-2"}
   [:p {:class "text-xs opacity-50 mb-3 tracking-wide uppercase"} "Опционально"]

   ;; фокус
   [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
    [:input {:type "checkbox"
             :_ "on change
                   if me.checked
                     remove [disabled] from #focus
                   else
                     add [disabled] to #focus"}]
    [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"} "Фокус"]
    [:div {:class "collapse-content"}
     [:div {:class "form-control"}
      [:input {:type "range" :name "focus" :min "0" :max "10" :value "5"
               :id "focus" :data-optional true :disabled true
               :class "range range-info" :style "height: 2rem"
               :_ "on input set #focus-value.textContent to my.value"}]
      [:div {:class "flex justify-between mt-0.5 px-0.5"}
       [:span {:class "text-xs opacity-40"} "0"]
       [:span {:id "focus-value" :class "text-sm font-semibold tabular-nums"} "5"]
       [:span {:class "text-xs opacity-40"} "10"]]]]]

   ;; сон
   [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
    [:input {:type "checkbox"
             :_ "on change
                   if me.checked
                     remove [disabled] from #sleep_hours
                   else
                     add [disabled] to #sleep_hours"}]
    [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"} "Сон (ч)"]
    [:div {:class "collapse-content"}
     [:input {:type "number" :name "sleep_hours" :id "sleep_hours"
              :data-optional true :disabled true
              :step "0.1" :min "0" :max "24"
              :placeholder "7.5"
              :class "input input-bordered w-full"}]]]

   ;; заметка
   [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
    [:input {:type "checkbox"
             :_ "on change
                   if me.checked
                     remove [disabled] from #note
                   else
                     add [disabled] to #note"}]
    [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"} "Заметка"]
    [:div {:class "collapse-content"}
     [:textarea {:name "note" :id "note" :rows "3" :maxlength "500"
                 :data-optional true :disabled true
                 :placeholder "..."
                 :class "textarea textarea-bordered w-full"}]]]

   ;; активность
   [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
    [:input {:type "checkbox"
             :_ "on change
                   if me.checked
                     remove [disabled] from #activity
                   else
                     add [disabled] to #activity"}]
    [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"} "Активность"]
    [:div {:class "collapse-content"}
     [:input {:type "text" :name "activity" :id "activity"
              :data-optional true :disabled true
              :placeholder "..."
              :class "input input-bordered w-full"}]]]]

  ;; кнопка сохранения (htmx-disabled на время запроса)
  [:button {:type "submit" :class "btn btn-primary w-full h-12 mt-4"}
   "Сохранить"]]


 ])

;; ============================================================

(def ^:private full-page
  [:html {:data-theme "dark"
          :lang "ru"
          :class "scroll-smooth"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1, viewport-fit=cover"}]
    [:title "Hiccup Preview"]
    ;; Tailwind + DaisyUI через CDN (play CDN — только для прототипа)
    [:link {:href "https://cdn.jsdelivr.net/npm/daisyui@4.12.10/dist/full.min.css"
            :rel "stylesheet"
            :type "text/css"}]
    [:script {:src "https://cdn.tailwindcss.com"}]
    ;; htmx + hyperscript — для интерактивности
    [:script {:src "https://unpkg.com/htmx.org@1.9.12"}]
    [:script {:src "https://unpkg.com/hyperscript.org@0.9.12"}]]
   [:body {:class "min-h-screen bg-base-200"}
    body]])

(defn -main
  []
  (let [output-path "resources/proto/preview.html"
        rendered    (str (html full-page))]
    (spit output-path rendered)
    (println (str "Готово: " output-path
                  "\nОткрой: file://" (System/getProperty "user.dir") "/" output-path))))

(-main)
