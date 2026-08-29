;; UI-скетч: страница /insights/new — форма создания инсайта (Фаза 3)
;; Источник: design.md Decision 13 (инсайт-артефакт, модель, подбор, fallback).
;; Этот файл — референс для имплементационного change `add-insights-artefact`.
;; Не production-код, а фиксация UX-дизайна.

;; ──────────────────────────────────────────────────────────────
;; 1. Страница /insights/new (форма создания)
;; ──────────────────────────────────────────────────────────────

(def insights-new-page
  [:div {:class "max-w-2xl mx-auto p-4 pb-24"}

   ;; ── Карточка: шапка (назад + заголовок) ──
   [:div {:class "card bg-base-200 shadow-sm mb-4"}
    [:div {:class "card-body p-4"}
     [:div {:class "flex items-center gap-3 mb-1"}
      [:a {:href "/insights"
           :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
           :aria-label "Назад"}
       [:svg {:xmlns "http://www.w3.org/2000/svg"
              :width 20 :height 20 :viewBox "0 0 24 24"
              :fill "none" :stroke "currentColor"
              :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M15 18l-6-6 6-6"}]]]
      [:h1 {:class "text-2xl font-bold"} "Новый инсайт"]]
     [:p {:class "text-sm opacity-70"}
      "Совет себе для момента, когда он понадобится"]]]

   ;; ── Карточка: форма ──
   [:div {:class "card bg-base-200 shadow-sm"}
    [:div {:class "card-body p-4"}

     ;; Контейнер ошибок валидации (мягкий alert, показывается сервером при ошибке)
     [:div {:id "form-error" :class "mb-2"}]

     [:form {:id "insight-form"
             :hx-post "/insights"
             :hx-ext "json-enc"
             :hx-target "#form-error"
             :hx-swap "innerHTML"
             :_ "on htmx:beforeRequest add .htmx-request to #submit-btn
                 on htmx:afterRequest remove .htmx-request from #submit-btn
                 on htmx:afterRequest if event.detail.redirected
                   then settle window.location.href = event.detail.path"
             :class "space-y-4"}

      ;; ── CSRF ──
      [:input {:type "hidden" :name "__anti-forgery-token" :value "CSRF"}]
      ;; ── entry_id (если создан из /feed или /check-in, иначе пусто) ──
      [:input {:type "hidden" :name "entry_id" :value ""}]

      ;; ── context (обязательно) ──
      [:div {:class "form-control"}
       [:label {:class "label" :for "context"}
        [:span {:class "label-text"} "Контекст состояния"]]
       [:textarea {:id "context" :name "context"
                   :class "textarea textarea-bordered w-full h-24"
                   :placeholder "В каком состоянии пришёл инсайт? Опиши контекст."
                   :required true}
        ""]
       [:label {:class "label"}
        [:span {:class "label-text-alt opacity-60"}
         "Опиши состояние, в котором пришёл инсайт"]]]

      ;; ── state_label (обязательно, select из 6 ярлыков) ──
      [:div {:class "form-control"}
       [:label {:class "label" :for "state_label"}
        [:span {:class "label-text"} "Состояние для подбора"]]
       [:select {:id "state_label" :name "state_label"
                 :class "select select-bordered w-full"
                 :required true}
        [:option {:value "" :disabled true :selected true} "Выбери состояние"]
        [:option {:value "mixed"} "Смешанное"]
        [:option {:value "anxiety"} "Тревога"]
        [:option {:value "elevated"} "Подъём"]
        [:option {:value "low"} "Спад"]
        [:option {:value "balanced"} "Ровное"]
        [:option {:value "neutral"} "Нейтральное"]]
       [:label {:class "label"}
        [:span {:class "label-text-alt opacity-60"}
         "По этому ярлыку система подберёт инсайт в будущем"]]]

      ;; ── category (обязательно, select) ──
      [:div {:class "form-control"}
       [:label {:class "label" :for "category"}
        [:span {:class "label-text"} "Категория"]]
       [:select {:id "category" :name "category"
                 :class "select select-bordered w-full"
                 :required true}
        [:option {:value "" :disabled true :selected true} "Выбери категорию"]
        [:option {:value "productivity"} "Продуктивность"]
        [:option {:value "coping"} "Копинг"]
        [:option {:value "identity"} "Идентичность"]
        [:option {:value "general"} "Общее"]]]

      ;; ── advice_to_self (динамический список) ──
      ;; Каждый пункт — textarea с hidden-индексом для JSON-serialisation.
      ;; Добавление: htmx-запрос возвращает новый пункт (variant A)
      ;;   или hyperscript клонирует шаблон (variant B — ниже).
      ;; Удаление: hyperscript removes the .advice-item, reindexes.
      ;; Минимум 1 пункт — валидируется на сервере.
      [:div {:class "form-control"}
       [:label {:class "label"}
        [:span {:class "label-text"} "Советы себе"]]
       [:p {:class "text-sm opacity-60 mb-2"}
        "Что работает в этом состоянии. Минимум один пункт."]

       ;; Контейнер пунктов (htmx-target для добавления)
       [:div {:id "advice-list" :class "space-y-2"}

        ;; ── Пункт #1 (дефолтный, всегда есть, без кнопки удаления) ──
        [:div {:class "advice-item flex items-start gap-2"}
         [:textarea {:name "advice_to_self[]"
                     :class "textarea textarea-bordered w-full h-20"
                     :placeholder "Например: дыхание 4-7-8 пять минут"
                     :required true}]]

        ;; ── Пункт #2 (пример добавленного, с кнопкой удаления) ──
        [:div {:class "advice-item flex items-start gap-2"}
         [:textarea {:name "advice_to_self[]"
                     :class "textarea textarea-bordered w-full h-20"
                     :placeholder "Например: текст близкому"
                     :required true}]
         [:button {:type "button"
                   :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
                   :aria-label "Удалить пункт"
                   :_ "on click remove closest .advice-item"}
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 :width 18 :height 18 :viewBox "0 0 24 24"
                 :fill "none" :stroke "currentColor"
                 :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
           [:path {:d "M18 6L6 18M6 6l12 12"}]]]]]

       ;; Кнопка «добавить пункт» — htmx-запрос на сервер (вариант A).
       ;; Возвращает новый .advice-item, вставляется в конец #advice-list.
       ;; json-enc НЕ нужен — это GET за HTML-фрагментом.
       [:button {:type "button"
                 :class "btn btn-ghost btn-sm h-11 min-h-11 mt-2"
                 :hx-get "/insights/advice-item"
                 :hx-target "#advice-list"
                 :hx-swap "beforeend"
                 :_ "on htmx:afterRequest
                       if first .advice-item in #advice-list has no button
                         then add a delete-button to it"}
        [:svg {:xmlns "http://www.w3.org/2000/svg"
               :width 20 :height 20 :viewBox "0 0 24 24"
               :fill "none" :stroke "currentColor"
               :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
         [:path {:d "M12 5v14M5 12h14"}]]
        "Добавить пункт"]]

      ;; ── identity (опционально) ──
      [:div {:class "form-control"}
       [:label {:class "label" :for "identity"}
        [:span {:class "label-text"} "Утверждение о себе"
         [:span {:class "opacity-50 ml-1"} "(необязательно)"]]]
       [:textarea {:id "identity" :name "identity"
                   :class "textarea textarea-bordered w-full h-20"
                   :placeholder "Например: я не своя тревога — я та, кто её наблюдает"}
        ""]
       [:label {:class "label"}
        [:span {:class "label-text-alt opacity-60"}
         "Ключевое утверждение о себе для этого состояния"]]]

      ;; ── Кнопка «сохранить» ──
      [:div {:class "form-control mt-4"}
       [:button {:id "submit-btn"
                 :type "submit"
                 :class "btn btn-primary h-11 min-h-11 w-full"}
        [:span {:class "loading loading-spinner loading-sm htmx-indicator"}]
        "Сохранить"]]]]]])

;; ──────────────────────────────────────────────────────────────
;; 2. Фрагмент: новый пункт advice (ответ на /insights/advice-item)
;; ──────────────────────────────────────────────────────────────
;; Сервер возвращает этот HTML при hx-get на «Добавить пункт».
;; Индекс/ID не нужен — json-enc сериализует все textarea[name="advice_to_self[]"]
;; в массив автоматически (стандартный HTML-массив-синтаксис).

(def advice-item-fragment
  [:div {:class "advice-item flex items-start gap-2"}
   [:textarea {:name "advice_to_self[]"
               :class "textarea textarea-bordered w-full h-20"
               :placeholder "Совет себе в этом состоянии"
               :required true}]
   [:button {:type "button"
             :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
             :aria-label "Удалить пункт"
             :_ "on click remove closest .advice-item"}
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :width 18 :height 18 :viewBox "0 0 24 24"
           :fill "none" :stroke "currentColor"
           :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
     [:path {:d "M18 6L6 18M6 6l12 12"}]]]])

;; ──────────────────────────────────────────────────────────────
;; 3. Фрагмент: ошибка валидации (ответ в #form-error)
;; ──────────────────────────────────────────────────────────────

(def validation-error-fragment
  [:div {:class "alert alert-warning shadow-sm"}
   [:svg {:xmlns "http://www.w3.org/2000/svg"
          :width 20 :height 20 :viewBox "0 0 24 24"
          :fill "none" :stroke "currentColor"
          :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
    [:circle {:cx 12 :cy 12 :r 10}]
    [:path {:d "M12 8v4M12 16h.01"}]]
   [:span {:class "text-sm"} "Заполни контекст, состояние и хотя бы один совет"]])

;; ──────────────────────────────────────────────────────────────
;; 4. Заметки по UX
;; ──────────────────────────────────────────────────────────────
;; - Шапка: «← назад» (htmx-навигация не нужна, обычная ссылка на /insights)
;;   + заголовок «Новый инсайт» + подзаголовок (поддерживающий, не объясняющий
;;   механику подбора — это раскрывается на /insights).
;; - Форма в одной карточке (card bg-base-200 shadow-sm) — все поля рядом,
;;   не разбивать на несколько карточек. Меньше скролла на мобиле.
;; - context: textarea h-24 (96px) — достаточно для 2–3 строк, не пугает
;;   большим пустым полем. Placeholder объясняет, что писать.
;; - state_label: select с 6 ярлыками, первый — disabled «выбери состояние».
;;   Если создан из /feed — selected=value из query param (сервер рендерит).
;; - category: select, аналогично. 4 категории из spec.
;; - advice_to_self: динамический список. Каждый пункт — textarea + кнопка ×
;;   (кроме первого, неудаляемого — минимум 1). Кнопка «Добавить пункт» —
;;   крупная, btn-ghost (не прятать), с иконкой-плюсом.
;; - identity: опционально, textarea h-20, placeholder с примером.
;;   Подсказка под полем — что это и зачем.
;; - Сохранение: htmx-post с json-enc, target=#form-error. Успех →
;;   сервер возвращает HX-Redirect: /insights (htmx сам редиректит).
;;   Кнопка показывает loading-spinner во время запроса (htmx-indicator).
;; - Ошибка валидации: alert-warning (мягкий, не error-red), в #form-error.
;;   Текст поддерживающий, не обвиняющий.
;; - Touch-targets: все кнопки h-11 min-h-11 (44×44px). Textarea —
;;   минимум h-20 (80px), удобно попадать пальцем.

;; ──────────────────────────────────────────────────────────────
;; 5. Логика добавления/удаления пунктов advice
;; ──────────────────────────────────────────────────────────────
;; Вариант A (htmx, реализован в скетче):
;;   - «Добавить пункт» → hx-get /insights/advice-item → сервер возвращает
;;     advice-item-fragment → вставляется beforeend в #advice-list.
;;   - Удаление: hyperscript «on click remove closest .advice-item».
;;   - Первый пункт: кнопка удаления не показывается (нельзя удалить единственный).
;;     При добавлении второго пункта — hyperscript добавляет кнопку удаления
;;     к первому (см. _ на кнопке «Добавить пункт»).
;;
;; Вариант B (hyperscript-only, альтернатива):
;;   - Клонирование шаблонного элемента <template> по hyperscript.
;;   - Меньше запросов, но сложнее поддерживать (сервер не контролирует
;;     разметку нового пункта).
;;   — Отклонён: htmx-вариант соответствует AGENTS.md («htmx-философия»)
;;     и проще в отладке.
;;
;; Сериализация: все textarea[name="advice_to_self[]"] собираются json-enc
;;   в массив. Сервер парсит JSON, валидирует (min 1 non-empty), сохраняет
;;   как JSON-массив в TEXT (Decision 13.2).

;; ──────────────────────────────────────────────────────────────
;; 6. Предзаполнение из /feed или /check-in
;; ──────────────────────────────────────────────────────────────
;; - /feed → /insights/new?entry_id=42&state_label=anxiety
;;   context предзаполнен: «Состояние: тревога (энергия 4, тревога 7, фокус 3)»
;;   state_label=anxiety (selected в select).
;;   entry_id=42 (hidden).
;; - /check-in (post-save) → /insights/new?entry_id=42&state_label=anxiety
;;   Аналогично.
;; - /insights/new (без query params) — пустая форма, state_label выбирается.
;; Рендер: сервер читает query params, подставляет в value/selected.
