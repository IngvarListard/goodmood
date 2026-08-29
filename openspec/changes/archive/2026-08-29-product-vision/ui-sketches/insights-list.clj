;; UI-скетч: страница /insights — список всех инсайтов (Фаза 3)
;; Источник: design.md Decision 13 (инсайт-артефакт, модель, подбор, fallback).
;; Этот файл — референс для имплементационного change `add-insights-artefact`.
;; Не production-код, а фиксация UX-дизайна.

;; ──────────────────────────────────────────────────────────────
;; 1. Страница /insights (заголовок + фильтры + список + онбординг)
;; ──────────────────────────────────────────────────────────────

(def insights-page
  [:div {:class "max-w-2xl mx-auto p-4 pb-24"}

   ;; ── Карточка: заголовок страницы ──
   [:div {:class "card bg-base-200 shadow-sm mb-4"}
    [:div {:class "card-body p-4"}
     [:div {:class "flex items-center justify-between mb-1"}
      [:h1 {:class "text-2xl font-bold"} "Инсайты"]
      [:a {:href "/insights/new"
           :class "btn btn-primary btn-sm h-11 min-h-11 px-4"}
       "Новый"]]
     [:p {:class "text-sm opacity-70"}
      "Советы себе, которые работают — в нужный момент"]]]

   ;; ── Карточка: фильтр по категории (tabs, htmx-свалоп) ──
   ;; hx-get="/insights?category=..." — сервер возвращает #insights-list
   [:div {:class "card bg-base-200 shadow-sm mb-4"}
    [:div {:class "card-body p-4"}
     [:div {:class "tabs tabs-boxed" :role "tablist"}
      [:a {:role "tab" :class "tab tab-active"
           :hx-get "/insights"
           :hx-target "#insights-list" :hx-select "#insights-list"
           :_ "on click remove .tab-active from .tab then add .tab-active to me"}
       "Все"]
      [:a {:role "tab" :class "tab"
           :hx-get "/insights?category=productivity"
           :hx-target "#insights-list" :hx-select "#insights-list"
           :_ "on click remove .tab-active from .tab then add .tab-active to me"}
       "Продуктивность"]
      [:a {:role "tab" :class "tab"
           :hx-get "/insights?category=coping"
           :hx-target "#insights-list" :hx-select "#insights-list"
           :_ "on click remove .tab-active from .tab then add .tab-active to me"}
       "Копинг"]
      [:a {:role "tab" :class "tab"
           :hx-get "/insights?category=identity"
           :hx-target "#insights-list" :hx-select "#insights-list"
           :_ "on click remove .tab-active from .tab then add .tab-active to me"}
       "Идентичность"]
      [:a {:role "tab" :class "tab"
           :hx-get "/insights?category=general"
           :hx-target "#insights-list" :hx-select "#insights-list"
           :_ "on click remove .tab-active from .tab then add .tab-active to me"}
       "Общее"]]]]

   ;; ── Контейнер списка ──
   [:div {:id "insights-list" :class "space-y-3"}

    ;; ── Карточка #1 ──
    [:div {:class "card bg-base-200 shadow-sm" :id "insight-1"}
     [:div {:class "card-body p-4"}
     [:div {:class "flex items-start justify-between gap-2 mb-2"}
      [:div {:class "flex items-center gap-2 flex-wrap"}
       [:span {:class "badge badge-secondary badge-sm"} "Копинг"]
       [:span {:class "badge badge-ghost badge-sm"} "тревога"]
       [:span {:class "text-xs opacity-50 ml-auto tabular-nums"} "14 авг"]]
      [:div {:class "flex items-center gap-1"}
       [:button {:type "button"
                 :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                 :hx-get "/insights/1/edit"
                 :hx-target "#insight-modal-box"
                 :_ "on htmx:afterRequest call #insight-modal.showModal()"}
        "Править"]
       [:button {:type "button"
                 :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                 :_ "on click toggle .modal-open on #del-insight-1"}
        [:svg {:xmlns "http://www.w3.org/2000/svg"
               :width 18 :height 18 :viewBox "0 0 24 24"
               :fill "none" :stroke "currentColor"
               :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
         [:path {:d "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"}]]]]]

     ;; context — 1–2 строки, клик раскрыть
     [:p {:class "text-sm mb-2"
          :_ "on click toggle .line-clamp-none from closest .card"}
      "Когда тревога 7+, я знаю, что это знакомое состояние, а не конец. Главное — не принимать решений в первые два часа."]

     ;; advice_to_self — первые 2 пункта + «…ещё N»
     [:ul {:class "text-sm space-y-1 opacity-85"}
      [:li
       [:span {:class "opacity-50 mr-1"} "•"]
       " Дыхание 4-7-8 минут пять — снижает накал"]
      [:li
       [:span {:class "opacity-50 mr-1"} "•"]
       " Текст близкому: «мне сейчас некомфортно, не срочно»"]]
     [:button {:type "button" :class "btn btn-ghost btn-xs mt-1"}
      "…ещё 3 совета"]

      ;; identity — одной строкой, курсивом, opacity 70%
      [:p {:class "text-sm italic opacity-70 mt-2"}
       "Я не своя тревога — я та, кто её наблюдает"]]] ;; ← конец card-body + card #1

    ;; ── Карточка #2 (без identity, категория productivity) ──
    [:div {:class "card bg-base-200 shadow-sm" :id "insight-2"}
     [:div {:class "card-body p-4"}
     [:div {:class "flex items-start justify-between gap-2 mb-2"}
      [:div {:class "flex items-center gap-2 flex-wrap"}
       [:span {:class "badge badge-secondary badge-sm"} "Продуктивность"]
       [:span {:class "badge badge-ghost badge-sm"} "подъём"]
       [:span {:class "text-xs opacity-50 ml-auto tabular-nums"} "10 авг"]]
      [:div {:class "flex items-center gap-1"}
       [:button {:type "button"
                 :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                 :hx-get "/insights/2/edit"
                 :hx-target "#insight-modal-box"
                 :_ "on htmx:afterRequest call #insight-modal.showModal()"}
        "Править"]
       [:button {:type "button"
                 :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                 :_ "on click toggle .modal-open on #del-insight-2"}
        [:svg {:xmlns "http://www.w3.org/2000/svg"
               :width 18 :height 18 :viewBox "0 0 24 24"
               :fill "none" :stroke "currentColor"
               :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
         [:path {:d "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"}]]]]]

     [:p {:class "text-sm mb-2"}
      "Энергия 8–9 — окно, когда могу сделать то, что откладывал неделями."]
      [:ul {:class "text-sm space-y-1 opacity-85"}
       [:li
        [:span {:class "opacity-50 mr-1"} "•"]
        " Сначала одно большое дело, до обеда"]
       [:li
        [:span {:class "opacity-50 mr-1"} "•"]
        " Не планировать больше 3 вещей на день"]]]] ;; ← конец card-body + card #2

    ;; ── Фильтр пуст: «В этой категории пока пусто» (закомментировано) ──
    ;; Показывается вместо карточек, когда category выбран и 0 инсайтов.
    #_[:div {:class "text-center py-10"}
       [:p {:class "opacity-70 mb-1"} "В этой категории пока пусто"]
       [:p {:class "text-sm opacity-50"}
        "Создай первый инсайт — кнопка «Новый» сверху"]]

    ] ;; ← конец #insights-list

   ;; ── Онбординг: нет ни одного инсайта (закомментировано) ──
   ;; Заменяет весь блок фильтров + список.
   #_[:div {:class "text-center py-16"}
      [:p {:class "text-lg mb-2"} "У тебя ещё нет инсайтов"]
      [:p {:class "text-sm opacity-70 mb-6 max-w-sm mx-auto"}
       "Инсайт — это совет себе, который работает. Ты записываешь его в одном состоянии, чтобы получить обратно, когда оно вернётся."]
      [:a {:href "/insights/new" :class "btn btn-primary"}
       "Создать первый"]]

   ;; ── Модалка подтверждения удаления (per-карточка, генерится сервером) ──
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
                :hx-target "#insights-list"
                :hx-swap "outerHTML"
                :_ "on htmx:afterRequest remove #insight-1 then call #del-insight-1.close()"}
       "Удалить"]]]
    [:form {:method "dialog" :class "modal-backdrop"}
     [:button "close"]]]

   ]) ;; ← конец page-div + def

;; ──────────────────────────────────────────────────────────────
;; 2. Карточка инсайта — переиспользуемый компонент
;; ──────────────────────────────────────────────────────────────
;; В имплементации: (defn insight-card [insight] ...)
;; Параметризуется: :id, :category, :state-label, :context,
;;                  :advice-to-self (vector), :identity (nilable),
;;                  :created-at, :extra-advice-count.
;; Локализация: бейджи категории и state_label через i18n/t.
;; Редактирование/удаление — htmx, без JS-логики (только hyperscript).

;; ──────────────────────────────────────────────────────────────
;; 3. Заметки по UX
;; ──────────────────────────────────────────────────────────────
;; - Заголовок страницы «Инсайты», subtitle — поддерживающий, не
;;   объясняющий механику (она раскрывается при первом создании).
;; - Фильтр-табы: daisyui `tabs tabs-boxed` — компактно на мобиле,
;;   горизонтальный скролл при необходимости. Активный таб —
;;   swap-цвет, без индикатора «выбрано» отдельным значком.
;; - Карточка: бейджи сверху (category — насыщенный, state_label —
;;   нейтральный `badge-ghost`), дата в той же строке справа мелким.
;; - context: `line-clamp-2` по умолчанию, клик по карточке
;;   раскрывает. НЕ ссылка, а toggle — чтобы не ломать хит-зону
;;   кнопок «править/удалить».
;; - advice_to_self: показываем первые 2 пункта + «…ещё N»
;;   (кнопка раскрывает остальные в той же карточке, htmx или
;;   hyperscript toggle скрытых li).
;; - identity: одной строкой, курсив, opacity 70%. Без отдельного
;;   заголовка («я-утверждение» и т.п.) — тон не клинический.
;; - Удаление: dialog с подтверждением (modal-bottom на мобиле =
;;   bottom-sheet). Текст поддерживающий, без «вы уверены?».
;; - Онбординг: заголовок + 2 строки объяснения + кнопка. НЕ
;;   обучающий modal — встроенный empty-state на странице.
;; - Фильтр-пусто: компактнее онбординга — только строка + подсказка.
;;   Кнопки «Новый» сверху достаточно, дублировать не нужно.

;; ──────────────────────────────────────────────────────────────
;; 4. Цветовая логика бейджей
;; ──────────────────────────────────────────────────────────────
;; - category: `badge-secondary` — спокойный, единый цвет для всех
;;   категорий. НЕ red/green — это не «ошибка/успех», а тип.
;;   Различение — по тексту («Копинг» / «Продуктивность» / …).
;; - state_label: `badge-ghost` — минимальный, нейтральный фон.
;;   Идентификатор состояния, не эмоциональная оценка.
;; - Дата: `opacity-50 tabular-nums` — мелкая, не привлекает.

;; ──────────────────────────────────────────────────────────────
;; 5. Touch-targets и доступность
;; ──────────────────────────────────────────────────────────────
;; - Все кнопки: `h-11 min-h-11` — минимум 44×44px (Apple HIG).
;; - «Править» — текстовая (не иконка), «Удалить» — иконка-мусорника
;;   (редкое действие, не загромождаем текстом).
;; - Dialog удаления: `modal-bottom sm:modal-middle` — на мобиле
;;   снизу (удобно большим пальцем), на десктопе — центр.
;; - Tablist: `role="tab"`, активный — `tab-active`. Клавиатура:
;;   стандартное поведение daisyui tabs.
