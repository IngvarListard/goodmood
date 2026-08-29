;; UI-скетч: страница /insights/:id — полный вид инсайта (Фаза 3)
;; Источник: design.md Decision 13 (инсайт-артефакт, редактирование in-place).
;; Этот файл — референс для имплементационного change `add-insights-artefact`.
;; Не production-код, а фиксация UX-дизайна.

;; ──────────────────────────────────────────────────────────────
;; 1. Страница /insights/:id (просмотр + in-place edit)
;; ──────────────────────────────────────────────────────────────
;; Редактирование — in-place htmx (Decision 13.8), НЕ отдельная страница.
;; Кнопка «править» возле поля → htmx-get возвращает editable-форму,
;; которая свапает read-блок на form-блок. Сохранение → свап обратно.

(def insights-show-page
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

;; ──────────────────────────────────────────────────────────────
;; 2. Фрагмент: inline-edit формы (ответы на hx-get «Править»)
;; ──────────────────────────────────────────────────────────────
;; Каждое поле свапается на свою форму независимо.
;; Сохранение -> свап обратно на read-блок.

;; ── 2a. Контекст — edit-mode ──
(def context-edit-fragment
  [:form {:id "context-block"
          :hx-post "/insights/1/context"
          :hx-ext "json-enc"
          :hx-target "this"
          :hx-swap "outerHTML"
          :class "space-y-2"}
   [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
   [:textarea {:name "context"
               :class "textarea textarea-bordered w-full h-24"}
    "Когда тревога 7+, я знаю, что это знакомое состояние, а не конец. Главное — не принимать решений в первые два часа. Тело напряжено, мысли скачут, но это уже было — и проходило."]
   [:div {:class "flex gap-2"}
    [:button {:type "submit" :class "btn btn-primary btn-sm h-11 min-h-11"}
     "Сохранить"]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11"
              :hx-get "/insights/1"
              :hx-target "#context-block"
              :hx-swap "outerHTML"}
     "Отмена"]]])

;; ── 2b. Советы — edit-mode (динамический список, как в /insights/new) ──
(def advice-edit-fragment
  [:form {:id "advice-block"
          :hx-post "/insights/1/advice"
          :hx-ext "json-enc"
          :hx-target "this"
          :hx-swap "outerHTML"
          :class "space-y-2"}
   [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
   [:div {:class "space-y-2"}
    [:div {:class "advice-item flex items-start gap-2"}
     [:textarea {:name "advice_to_self[]"
                 :class "textarea textarea-bordered w-full h-20"}
      "Дыхание 4-7-8 минут пять — снижает накал"]]
    [:div {:class "advice-item flex items-start gap-2"}
     [:textarea {:name "advice_to_self[]"
                 :class "textarea textarea-bordered w-full h-20"}
      "Текст близкому: «мне сейчас некомфортно, не срочно»"]
     [:button {:type "button"
               :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
               :aria-label "Удалить пункт"
               :_ "on click remove closest .advice-item"}
      [:svg {:xmlns "http://www.w3.org/2000/svg"
             :width 18 :height 18 :viewBox "0 0 24 24"
             :fill "none" :stroke "currentColor"
             :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
       [:path {:d "M18 6L6 18M6 6l12 12"}]]]
     [:div {:class "advice-item flex items-start gap-2"}
      [:textarea {:name "advice_to_self[]"
                  :class "textarea textarea-bordered w-full h-20"}
       "Не принимать решений в первые два часа"]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
                :aria-label "Удалить пункт"
                :_ "on click remove closest .advice-item"}
       [:svg {:xmlns "http://www.w3.org/2000/svg"
              :width 18 :height 18 :viewBox "0 0 24 24"
              :fill "none" :stroke "currentColor"
              :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M18 6L6 18M6 6l12 12"}]]]]]]
   [:button {:type "button"
             :class "btn btn-ghost btn-sm h-11 min-h-11"
             :hx-get "/insights/advice-item"
             :hx-target "#advice-block .advice-list-container"
             :hx-swap "beforeend"}
    "Добавить пункт"]
   [:div {:class "flex gap-2 mt-2"}
    [:button {:type "submit" :class "btn btn-primary btn-sm h-11 min-h-11"}
     "Сохранить"]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11"
              :hx-get "/insights/1"
              :hx-target "#advice-block"
              :hx-swap "outerHTML"}
     "Отмена"]]])

;; ── 2c. Идентичность — edit-mode ──
(def identity-edit-fragment
  [:form {:id "identity-block"
          :hx-post "/insights/1/identity"
          :hx-ext "json-enc"
          :hx-target "this"
          :hx-swap "outerHTML"
          :class "space-y-2"}
   [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
   [:textarea {:name "identity"
               :class "textarea textarea-bordered w-full h-20"
               :placeholder "Утверждение о себе для этого состояния"}
    "Я не своя тревога — я та, кто её наблюдает"]
   [:div {:class "flex gap-2"}
    [:button {:type "submit" :class "btn btn-primary btn-sm h-11 min-h-11"}
     "Сохранить"]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11"
              :hx-get "/insights/1"
              :hx-target "#identity-block"
              :hx-swap "outerHTML"}
     "Отмена"]]])

;; ──────────────────────────────────────────────────────────────
;; 3. Заметки по UX
;; ──────────────────────────────────────────────────────────────
;; - Шапка: «← назад» + бейджи категории и state_label.
;;   state_label — НЕ редактируемый (Decision 13.8 — привязан к контексту
;;   создания). Бейдж без кнопки правки.
;; - Четыре карточки: контекст, советы, идентичность, метаданные+действия.
;;   Каждое поле — в своей карточке, чтобы in-place edit свапал только
;;   одну карточку, не всю страницу.
;; - Контекст: полный текст, не обрезается (line-clamp нет).
;; - advice_to_self: нумерованный список <ol> — порядок имеет смысл
;;   (приоритет советов). Все пункты видны.
;; - identity: отдельная карточка, курсив opacity-80, лейбл «Идентичность».
;;   Если identity нет — карточка не рендерится (сервер: when-let).
;; - Метаданные: created_at, мелким, opacity-50. updated_at НЕ показываем
;;   пользователю (Decision 13.8 — упрощённое версионирование).
;; - Удаление: modal-bottom на мобиле, text-error на кнопке (не btn-error —
;;   мягче, не «опасность» а «деструктивное действие»).
;; - Редактирование — in-place htmx (Decision 13.8), НЕ отдельная страница
;;   /insights/:id/edit. Кнопка «Править» возле каждого поля свапает
;;   read-блок на form-блок. Сохранение → свап обратно.
;;   Альтернатива «отдельная страница edit» — отклонена: больше навигации,
;;   теряется контекст страницы.

;; ──────────────────────────────────────────────────────────────
;; 4. In-place edit — структура свапов
;; ──────────────────────────────────────────────────────────────
;;
;;   просмотр                          edit-mode
;;   ────────                          ─────────
;;   #context-block                    → hx-get /insights/1/edit-context
;;   ┌────────────────┐                ┌─────────────────────────┐
;;   │ context text   │     ПРАВИТЬ    │ <form>                  │
;;   │                │  ──────────▶   │   textarea              │
;;   └────────────────┘                │   [Сохранить] [Отмена]  │
;;                                     └─────────────────────────┘
;;                                       hx-post → свап обратно
;;
;;   #advice-block                     → hx-get /insights/1/edit-advice
;;   ┌────────────────┐                ┌─────────────────────────┐
;;   │ <ol>            │     ПРАВИТЬ    │ <form>                  │
;;   │  1. совет       │  ──────────▶   │   advice-list           │
;;   │  2. совет       │                │   [+ добавить]         │
;;   │  …             │                │   [Сохранить] [Отмена]  │
;;   └────────────────┘                └─────────────────────────┘
;;
;;   #identity-block                   → hx-get /insights/1/edit-identity
;;   ┌────────────────┐                ┌─────────────────────────┐
;;   │ italic text    │     ПРАВИТЬ    │ <form>                  │
;;   │                │  ──────────▶   │   textarea              │
;;   └────────────────┘                │   [Сохранить] [Отмена]  │
;;                                     └─────────────────────────┘
;;
;; category — edit через select в шапке (отдельный свап) — не в скетче,
;;   аналогично state_label, но редактируемый.

;; ──────────────────────────────────────────────────────────────
;; 5. Цветовая логика
;; ──────────────────────────────────────────────────────────────
;; - Карточки: bg-base-200 shadow-sm (как везде).
;; - Бейджи: category badge-secondary, state_label badge-ghost.
;; - Кнопка «Удалить»: btn-ghost + text-error — мягче, чем btn-error.
;;   Деструктивное действие, но не «тревога».
;; - Заголовки полей: opacity-60 uppercase tracking-wide — как
;;   «Сегодня» в feed.clj, единый паттерн.

;; ──────────────────────────────────────────────────────────────
;; 6. Touch-targets
;; ──────────────────────────────────────────────────────────────
;; - Все кнопки: h-11 min-h-11.
;; - «Править» — текстовая, возле каждого поля.
;; - «Удалить» — текст + иконка, в нижней карточке.
;; - Modal: modal-bottom на мобиле (удобно пальцем), sm:modal-middle.
