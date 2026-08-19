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
  [:div {:class "max-w-lg mx-auto px-2"}
 [:form {:hx-post "/entries"
         :hx-ext "json-enc"
         :hx-target "#entries-list"
         :hx-swap "afterbegin"
         :_ "on htmx:afterRequest if event.detail.successful me.reset()"
         :class "card bg-base-200 shadow-sm"}

  [:div {:class "card-body p-4 sm:p-6"}

   ;; ── Заголовок ──
   [:h2 {:class "card-title text-xl mb-0.5"} "Новая запись"]
   [:p {:class "text-sm opacity-60 mb-4"} "Как ты сейчас?"]

   ;; ── Переключатель шаблонов ──
   [:div {:class "tabs tabs-boxed mb-6"}
    [:a {:class "tab tab-active" :data-template "morning"
         :_ "on click remove .tab-active from .tab then add .tab-active to me"}
     "Утро"]
    [:a {:class "tab" :data-template "day"
         :_ "on click remove .tab-active from .tab then add .tab-active to me"}
     "День"]
    [:a {:class "tab" :data-template "evening"
         :_ "on click remove .tab-active from .tab then add .tab-active to me"}
     "Вечер"]
    [:a {:class "tab" :data-template "event"
         :_ "on click remove .tab-active from .tab then add .tab-active to me"}
     "Событие"]]

   ;; ── Контейнер ошибок (soft alert) ──
   [:div {:id "form-error" :class "mb-3"}]

   ;; ════════════════════════════════════════
   ;;  ЯДРО: 3 обязательных range
   ;; ════════════════════════════════════════

   ;; mood_score 0–10
   [:div {:class "form-control mb-5"}
    [:div {:class "label px-0"}
     [:span {:class "label-text text-base font-medium"} "Настроение"]]
    [:input {:type "range" :name "mood_score" :min "0" :max "10" :value "5"
             :class "range range-primary"
             :style "height: 2rem"
             :_ "on input put my value into #mood-value"}]
    [:div {:class "flex justify-between mt-0.5"}
     [:span {:class "text-xs opacity-40"} "0"]
     [:span {:id "mood-value" :class "text-sm font-semibold tabular-nums"} "5"]
     [:span {:class "text-xs opacity-40"} "10"]]]

   ;; energy 0–10
   [:div {:class "form-control mb-5"}
    [:div {:class "label px-0"}
     [:span {:class "label-text text-base font-medium"} "Энергия"]]
    [:input {:type "range" :name "energy" :min "0" :max "10" :value "5"
             :class "range range-success"
             :style "height: 2rem"
             :_ "on input put my value into #energy-value"}]
    [:div {:class "flex justify-between mt-0.5"}
     [:span {:class "text-xs opacity-40"} "0"]
     [:span {:id "energy-value" :class "text-sm font-semibold tabular-nums"} "5"]
     [:span {:class "text-xs opacity-40"} "10"]]]

   ;; anxiety 0–10
   [:div {:class "form-control mb-6"}
    [:div {:class "label px-0"}
     [:span {:class "label-text text-base font-medium"} "Тревога"]]
    [:input {:type "range" :name "anxiety" :min "0" :max "10" :value "5"
             :class "range range-warning"
             :style "height: 2rem"
             :_ "on input put my value into #anxiety-value"}]
    [:div {:class "flex justify-between mt-0.5"}
     [:span {:class "text-xs opacity-40"} "0"]
     [:span {:id "anxiety-value" :class "text-sm font-semibold tabular-nums"} "5"]
     [:span {:class "text-xs opacity-40"} "10"]]]

   ;; ════════════════════════════════════════
   ;;  ОПЦИОНАЛЬНЫЕ БЛОКИ (collapse)
   ;; ════════════════════════════════════════

   [:div {:class "border-t border-base-300 pt-4 mb-2"}
    [:p {:class "text-xs opacity-50 mb-3 tracking-wide uppercase"} "Дополнительно (необязательно)"]

    ;; ── Сон (часы) ──
    [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
     [:input {:type "checkbox"}]
     [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"}
      "Сон (часы)"]
     [:div {:class "collapse-content"}
      [:input {:type "number" :name "sleep_hours"
               :step "0.1" :min "0" :max "24"
               :placeholder "например, 7.5"
               :class "input input-bordered w-full"}]]]

    ;; ── Фокус (0–10) ──
    [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
     [:input {:type "checkbox"}]
     [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"}
      "Фокус"]
     [:div {:class "collapse-content"}
      [:input {:type "range" :name "focus" :min "0" :max "10" :value "5"
               :class "range range-info" :style "height: 2rem"
               :_ "on input put my value into #focus-value"}]
      [:div {:class "flex justify-between mt-0.5"}
       [:span {:class "text-xs opacity-40"} "0"]
       [:span {:id "focus-value" :class "text-sm font-semibold tabular-nums"} "5"]
       [:span {:class "text-xs opacity-40"} "10"]]]]

    ;; ── Заметка ──
    [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
     [:input {:type "checkbox"}]
     [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"}
      "Заметка"]
     [:div {:class "collapse-content"}
      [:textarea {:name "note" :rows "3" :maxlength "500"
                  :placeholder "Что происходит? Какие мысли?"
                  :class "textarea textarea-bordered w-full"}]]]

    ;; ── Активность ──
    [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
     [:input {:type "checkbox"}]
     [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"}
      "Активность"]
     [:div {:class "collapse-content"}
      [:input {:type "text" :name "activity"
               :placeholder "Чем занимался?"
               :class "input input-bordered w-full"}]]]]

   ;; ── Submit ──
   [:button {:type "submit"
             :class "btn btn-primary w-full h-12 text-base font-medium mt-4"}
    "Сохранить запись"]]]])

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
