;; UI-скетч: секция настроек уведомлений на /settings (Фаза 4)
;; Источник: design.md Decision 14.3 (таблица user_notification_settings).
;; Этот файл — референс для имплементационного change `add-coping-channels`.
;; Не production-код, а фиксация UX-дизайна.

;; ──────────────────────────────────────────────────────────────
;; 1. Данные и поток
;; ──────────────────────────────────────────────────────────────
;; Три слота: утро (08:00) / день (13:00) / вечер (19:00). Сохраняются в
;; user_notification_settings (user_id, slot, enabled, time) — 3 строки на
;; пользователя (design.md 14.3). Форма на POST /settings/notifications,
;; статус-фрагмент свапается в #notif-status.
;;
;; ВАЖНО (дизайн-решение 14.3): при in-app канале (Фаза 4) секция названа
;; «Сводки и подсказки» и помечена «в приложении», чтобы не обещать push
;; до Фазы 4.5. Промпт просит «Уведомления» — см. примечание в конце файла.

(def notification-settings
  [:section {:class "mb-6"}
   [:h2 {:class "text-sm font-medium opacity-60 mb-1 uppercase tracking-wide"}
    "Уведомления"]
   [:p {:class "text-xs opacity-50 mb-3"}
    "Содержат твои собственные инсайты, а не напоминания заполнить дневник."]

   [:div {:id "notif-status" :class "mb-2"}]

   [:form {:hx-post "/settings/notifications"
           :hx-target "#notif-status"
           :hx-swap "innerHTML"
           :class "card bg-base-200 p-4"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]

    ;; ── Утро ──
    [:div {:class "flex items-center justify-between gap-3"}
     [:span {:class "label-text"} "Утро"]
     [:div {:class "flex items-center gap-2"}
      [:input {:type "time" :name "time_morning" :value "08:00"
               :class "input input-sm input-bordered w-24"
               :disabled (not enabled-morning)}]
      [:input {:type "checkbox"
               :name "enabled_morning"
               :class "toggle toggle-primary"
               :checked enabled-morning}]]]

    [:div {:class "divider my-1"}]

    ;; ── День ──
    [:div {:class "flex items-center justify-between gap-3"}
     [:span {:class "label-text"} "День"]
     [:div {:class "flex items-center gap-2"}
      [:input {:type "time" :name "time_midday" :value "13:00"
               :class "input input-bordered w-24"
               :disabled (not enabled-midday)}]
      [:input {:type "checkbox"
               :name "enabled_midday"
               :class "toggle toggle-primary"
               :checked enabled-midday}]]]

    [:div {:class "divider my-1"}]

    ;; ── Вечер ──
    [:div {:class "flex items-center justify-between gap-3"}
     [:span {:class "label-text"} "Вечер"]
     [:div {:class "flex items-center gap-2"}
      [:input {:type "time" :name "time_evening" :value "19:00"
               :class "input input-bordered w-24"
               :disabled (not enabled-evening)}]
      [:input {:type "checkbox"
               :name "enabled_evening"
               :class "toggle toggle-primary"
               :checked enabled-evening}]]]

    [:button {:type "submit"
              :class "btn btn-primary w-full h-12 mt-4"}
     "Сохранить"]]])

;; ──────────────────────────────────────────────────────────────
;; 2. Статус-фрагмент (ответ POST) — «сохранено»
;; ──────────────────────────────────────────────────────────────
;; Свапается в #notif-status по hx-swap="innerHTML". Мягкий, dismissible.

(def notif-saved-status
  [:div {:id "notif-status" :class "alert alert-success shadow-sm"}
   [:div {:class "flex items-center gap-2 w-full"}
    [:span {:class "text-sm"} "Сохранено"]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
              :aria-label "Скрыть"
              :_ "on click add .hidden to me"}
     [:svg {:xmlns "http://www.w3.org/2000/svg"
            :width 16 :height 16 :viewBox "0 0 24 24"
            :fill "none" :stroke "currentColor"
            :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
      [:path {:d "M18 6L6 18M6 6l12 12"}]]]]])

;; ──────────────────────────────────────────────────────────────
;; 3. Заметки по UX
;; ──────────────────────────────────────────────────────────────
;; - Три строки, каждая: лейбл + time-input + toggle. Минимум нагрузки.
;; - Кнопка «Сохранить» — одна на все три слота (а не автосохранение на
;;   каждый toggle): меньше сетевых вызовов, понятнее состояние.
;; - Подзаголовок повторяет дизайн-принцип no-pressure (spec 8): уведомления
;;   несут ценность (инсайты), а не «ты пропустил день».
;; - alert-success «Сохранено» — спокойный, dismissible, не «ошибка!».
;; - Тёмная тема по умолчанию, toggle-primary (мягкий акцент).

;; ──────────────────────────────────────────────────────────────
;; 4. Touch-targets
;; ──────────────────────────────────────────────────────────────
;; - Toggle: DaisyUI toggle (≥44px touch).
;; - Кнопка «Сохранить»: h-12 (48px).
;; - Кнопка скрыть: h-11 min-h-11 (44px).
;; - Time-input: w-24 — узкий, но не первичное действие (поле ввода).

;; ──────────────────────────────────────────────────────────────
;; 5. Примечание о названии секции
;; ──────────────────────────────────────────────────────────────
;; Промпт просит заголовок «Уведомления». design.md Decision 14.3 предлагал
;; «Сводки и подсказки» + пометку «в приложении» (in-app канал, без push).
;; Если следовать промпту — «Уведомления», и подзаголовок уже снимает риск
;; обещания push. Если следовать design.md — секция называется «Сводки и
;; подсказки». Оба валидны; решение — за имплементационным change.