;; UI-скетч: страница реестра медикаментов (Фаза 1)
;; Валидировано через hiccup2.core/html — все векторы корректны.
;; Источник: design.md Decision 4а (модель данных медикаментов).
;; Этот файл — референс для имплементационного change `add-medications`.
;; Не production-код, а фиксация UX-дизайна.

;; ──────────────────────────────────────────────────────────────
;; 1. Страница /medications (каркас + карточки + collapse + диалог)
;; ──────────────────────────────────────────────────────────────

(def medications-page
  [:div {:class "max-w-2xl mx-auto p-4"}

   ;; ── Заголовок ──
   [:div {:class "flex items-center justify-between mb-1"}
    [:h1 {:class "text-2xl font-bold"} "Медикаменты"]
    [:button {:type "button"
              :class "btn btn-primary btn-sm h-11 min-h-11 px-4"
              :hx-get "/medications/new"
              :hx-target "#med-modal-box"
              :_ "on htmx:afterRequest call #med-modal.showModal()"}
     "Добавить"]]
   [:p {:class "text-sm opacity-70 mb-6"} "Препараты, которые вы принимаете"]

   ;; ── Контейнер ошибок ──
   [:div {:id "med-error" :class "mb-3"}]

   ;; ── Список активных медикаментов ──
   ;; Сервер: (map med-card medications) если (seq medications), иначе empty-state
   [:div {:id "med-list" :class "space-y-3"}

    ;; ── Карточка #1 (с sensitive-бейджем, 2 слота) ──
    [:div {:class "card bg-base-200 p-4" :id "med-1"}
     [:div {:class "flex items-start justify-between gap-2"}
      [:div
       [:h3 {:class "text-lg font-semibold"} "Препарат А"]
       [:p {:class "text-sm opacity-70"} "600 мг · 08:00, 20:00"]]
      [:div {:class "flex items-center gap-2"}
       [:span {:class "badge badge-neutral badge-sm"} "sensitive"]
       [:button {:type "button"
                 :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                 :hx-get "/medications/1/edit"
                 :hx-target "#med-modal-box"
                 :_ "on htmx:afterRequest call #med-modal.showModal()"}
        "Редактировать"]]]
     ;; Сегодняшний приём — чекбоксы per slot
     [:form {:hx-post "/medications/1/log"
             :hx-ext "json-enc"
             :hx-target "#med-1-slots"
             :hx-swap "outerHTML"
             :class "mt-3"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
      [:input {:type "hidden" :name "log_date" :value "2026-08-20"}]
      [:div {:id "med-1-slots" :class "flex items-center gap-4"}
       [:p {:class "text-sm opacity-50 mr-2"} "Сегодня:"]
       [:label {:class "flex items-center gap-2 cursor-pointer"}
        [:input {:type "checkbox" :name "scheduled_time" :value "08:00"
                 :class "checkbox checkbox-lg checkbox-success" :checked true
                 :_ "on change trigger htmx:submit from the closest <form/>"}]
        [:span {:class "text-sm"} "08:00"]]
       [:label {:class "flex items-center gap-2 cursor-pointer"}
        [:input {:type "checkbox" :name "scheduled_time" :value "20:00"
                 :class "checkbox checkbox-lg"
                 :_ "on change trigger htmx:submit from the closest <form/>"}]
        [:span {:class "text-sm"} "20:00"]]]]
     [:div {:class "mt-3 pt-3 border-t border-base-300"}
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 text-sm opacity-60"
                :hx-post "/medications/1/deactivate"
                :hx-target "#med-list"
                :hx-swap "outerHTML"
                :hx-confirm "Деактивировать Препарат А? История приёма сохранится."}
       "Деактивировать"]]]

    ;; ── Карточка #2 (без sensitive, 1 слот) ──
    [:div {:class "card bg-base-200 p-4" :id "med-2"}
     [:div {:class "flex items-start justify-between gap-2"}
      [:div
       [:h3 {:class "text-lg font-semibold"} "Препарат Г"]
       [:p {:class "text-sm opacity-70"} "200 мг · 08:00"]]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                :hx-get "/medications/2/edit"
                :hx-target "#med-modal-box"
                :_ "on htmx:afterRequest call #med-modal.showModal()"}
       "Редактировать"]]
     [:form {:hx-post "/medications/2/log"
             :hx-ext "json-enc"
             :hx-target "#med-2-slots"
             :hx-swap "outerHTML"
             :class "mt-3"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
      [:input {:type "hidden" :name "log_date" :value "2026-08-20"}]
      [:div {:id "med-2-slots" :class "flex items-center gap-4"}
       [:p {:class "text-sm opacity-50 mr-2"} "Сегодня:"]
       [:label {:class "flex items-center gap-2 cursor-pointer"}
        [:input {:type "checkbox" :name "scheduled_time" :value "08:00"
                 :class "checkbox checkbox-lg checkbox-success" :checked true
                 :_ "on change trigger htmx:submit from the closest <form/>"}]
        [:span {:class "text-sm"} "08:00"]]]]
     [:div {:class "mt-3 pt-3 border-t border-base-300"}
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 text-sm opacity-60"
                :hx-post "/medications/2/deactivate"
                :hx-target "#med-list"
                :hx-swap "outerHTML"
                :hx-confirm "Деактивировать Препарат Г? История приёма сохранится."}
       "Деактивировать"]]]]

   ;; ── Неактивные медикаменты (collapse, свёрнуты) ──
   [:div {:class "collapse collapse-arrow bg-base-200/50 mt-6 rounded-lg"}
    [:input {:type "checkbox"}]
    [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"}
     "Неактивные (1)"]
    [:div {:class "collapse-content space-y-3"}
     [:div {:class "card bg-base-300/50 p-4" :id "med-3"}
      [:div {:class "flex items-start justify-between gap-2"}
       [:div
        [:h3 {:class "text-base font-medium opacity-70"} "Препарат Д"]
        [:p {:class "text-sm opacity-50"} "50 мг · деактивирован 2026-07-15"]]
       [:button {:type "button"
                 :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                 :hx-post "/medications/3/activate"
                 :hx-target "#med-list"
                 :hx-swap "outerHTML"}
        "Активировать"]]]]]

   ;; ── Модалка (содержимое загружается htmx через hx-get) ──
   [:dialog {:id "med-modal" :class "modal"}
    [:div {:class "modal-box" :id "med-modal-box"}]
    [:form {:method "dialog" :class "modal-backdrop"}
     [:button "close"]]]])

;; ──────────────────────────────────────────────────────────────
;; 2. Форма добавления/редактирования (содержимое #med-modal-box)
;;    Загружается через hx-get /medications/new или /medications/:id/edit
;; ──────────────────────────────────────────────────────────────

(def medication-form
  [:div
   [:h3 {:class "text-lg font-semibold mb-4"} "Новый медикамент"]
   [:form {:hx-post "/medications"
           :hx-ext "json-enc"
           :hx-target "#med-list"
           :hx-swap "outerHTML"
           :_ "on htmx:afterRequest
                 if event.detail.successful
                   call #med-modal.close()
                 end"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]

    ;; name
    [:div {:class "form-control mb-4"}
     [:label {:class "label"}
      [:span {:class "label-text text-base font-medium"} "Название"]]
     [:input {:type "text" :name "name" :required true
              :placeholder "Препарат А"
              :class "input input-bordered w-full h-12"}]]

    ;; dose + dose_unit (в одной строке)
    [:div {:class "flex gap-2 mb-4"}
     [:div {:class "form-control flex-1"}
      [:label {:class "label"}
       [:span {:class "label-text text-base font-medium"} "Доза"]]
      [:input {:type "number" :name "dose" :required true
               :step "0.5" :min "0"
               :placeholder "600"
               :class "input input-bordered w-full h-12"}]]
     [:div {:class "form-control w-28"}
      [:label {:class "label"}
       [:span {:class "label-text text-base font-medium"} "Единица"]]
      [:select {:name "dose_unit" :class "select select-bordered w-full h-12"}
       [:option {:value "мг"} "мг"]
       [:option {:value "мл"} "мл"]
       [:option {:value "таб"} "таб"]
       [:option {:value "кап"} "кап"]]]]

    ;; schedule (text, comma-separated → server converts to JSON array)
    [:div {:class "form-control mb-4"}
     [:label {:class "label"}
      [:span {:class "label-text text-base font-medium"} "Расписание"]]
     [:input {:type "text" :name "schedule"
              :placeholder "08:00, 20:00"
              :class "input input-bordered w-full h-12"}]
     [:label {:class "label"}
      [:span {:class "label-text-alt text-xs opacity-50"} "Время приёма через запятую"]]]

    ;; sensitive (toggle)
    [:div {:class "form-control mb-4 flex-row items-center justify-between"}
     [:span {:class "text-base font-medium"} "Особо чувствительный"]
     [:input {:type "checkbox" :name "sensitive" :value "1"
              :class "toggle toggle-neutral"}]]

    ;; notes (textarea, опционально)
    [:div {:class "form-control mb-4"}
     [:label {:class "label"}
      [:span {:class "label-text text-base font-medium"} "Заметки"]]
     [:textarea {:name "notes" :rows "2" :maxlength "500"
                 :placeholder "Дополнительно..."
                 :class "textarea textarea-bordered w-full"}]]

    ;; кнопки
    [:div {:class "modal-action mt-6"}
     [:button {:type "submit"
               :class "btn btn-primary h-12 px-6"} "Сохранить"]
     [:button {:type "button"
               :class "btn btn-ghost h-12"
               :_ "on click call #med-modal.close()"} "Отмена"]]]])

;; ──────────────────────────────────────────────────────────────
;; 3. Empty state (когда med-list пуст — нет медикаментов)
;; ──────────────────────────────────────────────────────────────

(def medications-empty-state
  [:div {:class "text-center py-12"}
   [:p {:class "text-lg opacity-60 mb-2"} "Здесь будут ваши медикаменты"]
   [:p {:class "text-sm opacity-50 mb-6"}
    "Добавьте препараты, которые вы принимаете, чтобы отслеживать приём"]
   [:button {:type "button"
             :class "btn btn-primary h-12 px-6"
             :hx-get "/medications/new"
             :hx-target "#med-modal-box"
             :_ "on htmx:afterRequest call #med-modal.showModal()"}
    "Добавить медикамент"]])
