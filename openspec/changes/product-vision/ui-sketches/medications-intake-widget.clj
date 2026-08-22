;; UI-скетч: виджет быстрой отметки приёма медикаментов (Фаза 1)
;; Валидировано через hiccup2.core/html — все векторы корректны.
;; Источник: design.md Decision 4а (модель данных медикаментов).
;; Этот файл — референс для имплементационного change `add-medications`.
;; Не production-код, а фиксация UX-дизайна.

;; ──────────────────────────────────────────────────────────────
;; 1. Виджет «отметить приём» — список сегодняшних слотов
;;    Встраивается в /medications (верх страницы) или в форму записи
;; ──────────────────────────────────────────────────────────────

(def today-intake-widget
  [:div {:id "intake-widget" :class "space-y-3"}

   ;; ── Заголовок виджета ──
   [:div {:class "flex items-center gap-2 mb-1"}
    [:h2 {:class "text-lg font-semibold"} "Сегодня"]
    [:span {:class "text-sm opacity-50"} "20 августа"]]

   ;; ── Слот: Препарат А 600 мг, 08:00 — ПРИНЯТ ──
   [:form {:id "slot-1-08"
           :hx-post "/medications/1/log"
           :hx-ext "json-enc"
           :hx-target "#slot-1-08"
           :hx-swap "outerHTML"
           :class "card bg-base-200 p-3"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
    [:input {:type "hidden" :name "medication_id" :value "1"}]
    [:input {:type "hidden" :name "log_date" :value "2026-08-20"}]
    [:input {:type "hidden" :name "scheduled_time" :value "08:00"}]

    [:div {:class "flex items-center justify-between gap-3"}
     ;; Левая часть: название + доза + время
     [:div {:class "flex items-center gap-3"}
      [:div
       [:p {:class "font-medium text-base"} "Препарат А"]
       [:p {:class "text-sm opacity-60"} "600 мг · 08:00"]]
      ;; Бейдж статуса — принят (спокойный зелёный)
      [:span {:class "badge badge-success badge-sm"} "принят"]]
     ;; Кнопки — скрыты, т.к. уже принят. Только «отменить».
     [:button {:type "submit"
               :name "status" :value "pending"
               :class "btn btn-ghost btn-sm h-11 min-h-11 px-3 text-sm opacity-50"}
      "Отменить"]]]

   ;; ── Слот: Препарат А 600 мг, 20:00 — НЕ ОТМЕЧЕН (дефолт) ──
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
     [:div
      [:p {:class "font-medium text-base"} "Препарат А"]
      [:p {:class "text-sm opacity-60"} "600 мг · 20:00"]]
     ;; Две кнопки: принял / пропустил
     [:div {:class "flex items-center gap-2"}
      [:button {:type "submit"
                :name "status" :value "taken"
                :class "btn btn-success btn-sm h-11 min-h-11 px-4"}
       "Принял"]
      [:button {:type "submit"
                :name "status" :value "skipped"
                :class "btn btn-warning btn-sm h-11 min-h-11 px-4"}
       "Пропустил"]]]]

   ;; ── Слот: Препарат Г 200 мг, 08:00 — ПРОПУЩЕН ──
   [:form {:id "slot-2-08"
           :hx-post "/medications/2/log"
           :hx-ext "json-enc"
           :hx-target "#slot-2-08"
           :hx-swap "outerHTML"
           :class "card bg-base-200 p-3"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
    [:input {:type "hidden" :name "medication_id" :value "2"}]
    [:input {:type "hidden" :name "log_date" :value "2026-08-20"}]
    [:input {:type "hidden" :name "scheduled_time" :value "08:00"}]

    [:div {:class "flex items-center justify-between gap-3"}
     [:div {:class "flex items-center gap-3"}
      [:div
       [:p {:class "font-medium text-base"} "Препарат Г"]
       [:p {:class "text-sm opacity-60"} "200 мг · 08:00"]]
      ;; Бейдж статуса — пропущен (мягкий жёлтый, не красный)
      [:span {:class "badge badge-warning badge-sm"} "пропущен"]]
     ;; Отмена — пользователь может передумать
     [:button {:type "submit"
               :name "status" :value "pending"
               :class "btn btn-ghost btn-sm h-11 min-h-11 px-3 text-sm opacity-50"}
      "Отменить"]]]])


;; ──────────────────────────────────────────────────────────────
;; 2. Серверный ответ — обновлённая карточка слота
;;    Возвращается после htmx-submit, замещает форму через outerHTML.
;;    Пример: пользователь нажал «Принял» на слоте 20:00 → этот ответ.
;; ──────────────────────────────────────────────────────────────

(def intake-slot-taken-response
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


;; ──────────────────────────────────────────────────────────────
;; 3. Серверный ответ — слот пропущен
;;    Возвращается после htmx-submit со status=skipped.
;; ──────────────────────────────────────────────────────────────

(def intake-slot-skipped-response
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
     [:span {:class "badge badge-warning badge-sm"} "пропущен"]]
    [:button {:type "submit"
              :name "status" :value "pending"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-3 text-sm opacity-50"}
     "Отменить"]]])
