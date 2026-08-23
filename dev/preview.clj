(ns preview
  "Рендерит hiccup-вектор в HTML-файл для предпросмотра в браузере.

  Использование:
    1. Вставь свой hiccup-вектор в body (ниже, помечено PLACEHOLDER).
    2. Запусти: clj -M -i dev/preview.clj
    3. Открой в браузере: resources/proto/preview.html

  DaisyUI/Tailwind подключены через CDN, тёмная тема по умолчанию."
  (:require [hiccup2.core :refer [html]]))

;; === PLACEHOLDER: вставь свой hiccup сюда ===
;; Макет из design/what-now-button.clj
(def body
  [:div {:id "check-in-insight-toast"
         :class "alert alert-info shadow-lg fixed bottom-24 right-4 max-w-sm z-50"
         :hx-swap-oob "true"
         :role "status"}
   [:div {:class "flex items-start gap-3 w-full"}

    ;; ── Иконка-лампочка (спокойная, не стимулирующая) ──
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :width 20 :height 20 :viewBox "0 0 24 24"
           :fill "none" :stroke "currentColor"
           :stroke-width 1.8 :stroke-linecap "round" :stroke-linejoin "round"}
     [:path {:d "M12 18v-5.25m0 0a6.01 6.01 0 0 0 1.5-.189m-1.5.189a6.01 6.01 0 0 1-1.5-.189m3.75 7.429a3.75 3.75 0 1 1-7.5 0V8.25M8.25 8.25a3.75 3.75 0 0 1 7.5 0"}]
     [:path {:d "M9 18h6"}]
     [:path {:d "M10 21h4"}]]

    [:div {:class "flex-1 min-w-0"}
     [:p {:class "text-sm font-medium"}
      "В таком состоянии тебе помогало:"]
     [:p {:class "text-sm opacity-80 mt-1 line-clamp-1"}
      "Когда тревога высокая, а энергии мало."]
     [:p {:class "text-sm opacity-70 mt-1 line-clamp-1"}
      "Дыхание 4-7-8 минут пять — снижает накал."]
     [:a {:href "/insights/3"
          :class "link link-hover text-sm opacity-70 mt-1 inline-block"}
      "посмотреть полностью →"]]

    ;; ── Dismissible × ──
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
              :aria-label "Скрыть подсказку"
              :_ "on click add .hidden to me"}
     [:svg {:xmlns "http://www.w3.org/2000/svg"
            :width 18 :height 18 :viewBox "0 0 24 24"
            :fill "none" :stroke "currentColor"
            :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
      [:path {:d "M18 6L6 18M6 6l12 12"}]]]]])

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
