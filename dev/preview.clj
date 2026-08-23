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

   ;; ── Карточка: шапка (назад + бейджи) ──
   [:div {:class "card bg-base-200 shadow-sm mb-4"}
    [:div {:class "card-body p-4"}
     [:div {:class "flex items-center gap-3 mb-2"}
      [:a {:href "/insights"
           :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
           :aria-label "Назад"}
       [:svg {:xmlns "http://www.w3.org/2000/svg"
              :width 20 :height 20 :viewBox "0 0 24 24"
              :fill "none" :stroke "currentColor"
              :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M15 18l-6-6 6-6"}]]]
      [:h1 {:class "text-2xl font-bold"} "Инсайт"]]
     [:div {:class "flex items-center gap-2 flex-wrap"}
      [:span {:class "badge badge-secondary badge-sm"} "Копинг"]
      [:span {:class "badge badge-ghost badge-sm"} "тревога"]]]]

   ;; ── Карточка: контекст ──
   [:div {:class "card bg-base-200 shadow-sm mb-4"}
    [:div {:class "card-body p-4"}
     [:div {:class "flex items-center justify-between mb-2"}
      [:h2 {:class "text-sm font-medium opacity-60 uppercase tracking-wide"}
       "Контекст"]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                :hx-get "/insights/1/edit-context"
                :hx-target "#context-block"
                :hx-swap "outerHTML"}
       "Править"]]
     [:div {:id "context-block"}
      [:p {:class "text-sm"}
       "Когда тревога 7+, я знаю, что это знакомое состояние, а не конец. Главное — не принимать решений в первые два часа. Тело напряжено, мысли скачут, но это уже было — и проходило."]]]]

   ;; ── Карточка: советы себе (advice_to_self) ──
   [:div {:class "card bg-base-200 shadow-sm mb-4"}
    [:div {:class "card-body p-4"}
     [:div {:class "flex items-center justify-between mb-2"}
      [:h2 {:class "text-sm font-medium opacity-60 uppercase tracking-wide"}
       "Советы себе"]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                :hx-get "/insights/1/edit-advice"
                :hx-target "#advice-block"
                :hx-swap "outerHTML"}
       "Править"]]
     [:div {:id "advice-block"}
      [:ol {:class "text-sm space-y-2 list-decimal list-inside"}
       [:li "Дыхание 4-7-8 минут пять — снижает накал"]
       [:li "Текст близкому: «мне сейчас некомфортно, не срочно»"]
       [:li "Не принимать решений в первые два часа"]
       [:li "Прогулка 15 минут — меняет контекст тела"]
       [:li "Напомнить себе: это состояние, а не я"]]]]]

   ;; ── Карточка: идентичность (опционально) ──
   [:div {:class "card bg-base-200 shadow-sm mb-4"}
    [:div {:class "card-body p-4"}
     [:div {:class "flex items-center justify-between mb-2"}
      [:h2 {:class "text-sm font-medium opacity-60 uppercase tracking-wide"}
       "Идентичность"]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                :hx-get "/insights/1/edit-identity"
                :hx-target "#identity-block"
                :hx-swap "outerHTML"}
       "Править"]]
     [:div {:id "identity-block"}
      [:p {:class "text-sm italic opacity-80"}
       "Я не своя тревога — я та, кто её наблюдает"]]]]

   ;; ── Карточка: метаданные + действия ──
   [:div {:class "card bg-base-200 shadow-sm"}
    [:div {:class "card-body p-4"}
     [:div {:class "flex items-center justify-between flex-wrap gap-2"}
      [:span {:class "text-xs opacity-50 tabular-nums"}
       "Создано 14 августа 2026"]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-3 text-error"
                :_ "on click toggle .modal-open on #del-insight-1"}
       [:svg {:xmlns "http://www.w3.org/2000/svg"
              :width 18 :height 18 :viewBox "0 0 24 24"
              :fill "none" :stroke "currentColor"
              :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"}]]
       "Удалить"]]]]

   ;; ── Модалка подтверждения удаления ──
   [:dialog {:id "del-insight-1" :class "modal modal-bottom sm:modal-middle"}
    [:div {:class "modal-box"}
     [:h3 {:class "text-lg font-bold mb-2"} "Удалить инсайт?"]
     [:p {:class "text-sm opacity-70 mb-4"}
      "Действие необратимо. Записи и состояния сохранятся."]
     [:div {:class "modal-action"}
      [:form {:method "dialog"}
       [:button {:class "btn btn-ghost h-11 min-h-11"} "Отмена"]]
      [:button {:class "btn btn-error h-11 min-h-11"
                :hx-delete "/insights/1"
                :_ "on htmx:afterRequest
                       remove #insight-page
                       then settle window.location.href = '/insights'"}
       "Удалить"]]]
    [:form {:method "dialog" :class "modal-backdrop"}
     [:button "close"]]]])

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
