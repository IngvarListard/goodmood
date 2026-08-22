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
   [:form {:id "slot-1-20"
        :hx-post "/medications/1/log"
        :hx-ext "json-enc"
        :hx-target "#slot-1-20"
        :hx-swap "outerHTML"
        :class "card bg-base-200 p-3"}
 [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
 [:input {:type "hidden" :name "medication_id" :value "1"}]
 [:input {:type "hidden" :name "log_date" :value "2026-08-20"}]
 [:input {:type "hidden" :name "scheduled_time" :value "20:00"}]
 [:div {:class "flex items-center justify-between gap-3"}
  [:div {:class "flex items-center gap-3"}
   [:div
    [:p {:class "font-medium text-base"} "Препарат А"]
    [:p {:class "text-sm opacity-60"} "600 мг · 20:00"]]
   [:span {:class "badge badge-success badge-sm"} "принят"]]
  [:button {:type "submit"
            :name "status" :value "pending"
            :class "btn btn-ghost btn-sm h-11 min-h-11 px-3 text-sm opacity-50"}
   "Отменить"]]])

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
