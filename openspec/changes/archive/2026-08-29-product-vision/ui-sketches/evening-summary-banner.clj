;; UI-скетч: вечерняя сводка «на завтра» (Фаза 4, OQ6)
;; Источник: design.md Decision 16 (resolve OQ6) + Decision 14.2 (поля-сторожа).
;; Этот файл — референс для имплементационного change `add-coping-channels`.
;; Не production-код, а фиксация UX-дизайна.

;; ──────────────────────────────────────────────────────────────
;; 1. Триггер показа
;; ──────────────────────────────────────────────────────────────
;; Баннер появляется при открытии /feed, если:
;;   1) локальное время >= 18:00,
;;   2) сегодня есть записи,
;;   3) сводка ещё не показана сегодня (dismiss на сегодня).
;;
;; Хранение «показано ли сегодня»: поле-сторож last_summary_date на
;; user_notification_settings (design.md 14.2) — server-side, переживает
;; смену устройства/браузера. localStorage (чисто клиентский) — резервный
;; вариант. В этом скетче показан server-side: сервер решает, рендерить
;; баннер или нет.
;;
;; Dismiss (x) -> POST /notifications/summary-dismiss ставит
;; last_summary_date = сегодня. До 18:00 и при «уже показано» сервер просто
;; не рендерит баннер (никаких пустых мест).

;; ──────────────────────────────────────────────────────────────
;; 2. Баннер — свёрнутое состояние
;; ──────────────────────────────────────────────────────────────
;; Контейнер оборачивает и свёрнутое, и раскрытое состояние.
;; Тап по телу баннера -> toggle раскрытия (hyperscript).
;; Параметры (в имплементации):
;;   csrf-token — anti-forgery для dismiss-POST;
;;   insight — релевантный инсайт (:id, :context, :advice-to-self) или nil
;;             (пониженный сценарий — см. состояние 4.3).

(def evening-summary-banner
  [:div {:class "alert alert-info shadow-md mb-4" :id "summary-banner"}

   ;; ── Заголовок-строка (всегда видима) ──
   [:div {:class "flex items-center gap-3 w-full flex-wrap"
          :_ "on click toggle .summary-open on me"}
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :width 20 :height 20 :viewBox "0 0 24 24"
           :fill "none" :stroke "currentColor"
           :stroke-width 1.8 :stroke-linecap "round" :stroke-linejoin "round"}
     [:path {:d "M12 18v-5.25m0 0a6.01 6.01 0 0 0 1.5-.189m-1.5.189a6.01 6.01 0 0 1-1.5-.189m3.75 7.429a3.75 3.75 0 1 1-7.5 0V8.25M8.25 8.25a3.75 3.75 0 0 1 7.5 0"}]
     [:path {:d "M9 18h6"}]
     [:path {:d "M10 21h4"}]]

    [:p {:class "text-sm font-medium flex-1"} "Сводка на завтра готова"]

    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
              :_ "on click toggle .summary-expanded on closest section"}
     [:span {:class "summary-toggle-label"} "посмотреть"]]

    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
              :aria-label "Скрыть на сегодня"
              :hx-post "/notifications/summary-dismiss"
              :hx-target "this"
              :hx-swap "outerHTML"
              :hx-vals "{\"__anti-forgery-token\": \"tok\"}"
              :_ "on htmx:afterRequest remove #summary-banner"}
     [:svg {:xmlns "http://www.w3.org/2000/svg"
            :width 18 :height 18 :viewBox "0 0 24 24"
            :fill "none" :stroke "currentColor"
            :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
      [:path {:d "M18 6L6 18M6 6l12 12"}]]]]

   ;; ── Раскрытый контент (toggle) ──
   [:div {:class "hidden space-y-2 mt-1" :data-summary-open "true"}
    [:p {:class "text-sm opacity-85"} "Сегодня ты в состоянии «тревога»."]
    [:div {:class "card bg-base-200/60 border border-base-300 p-3 mt-1"}
     [:p {:class "text-xs uppercase tracking-wide opacity-50 mb-1"}
      "В прошлый раз в тревоге тебе помогло"]
     [:p {:class "text-sm opacity-85"} "Когда тревога высокая, а энергии мало."]
     [:p {:class "text-sm opacity-70"} "Дыхание 4-7-8 минут пять — снижает накал."]
     [:a {:href "/insights/3"
          :class "link link-hover text-sm opacity-70 mt-1 inline-block"}
      "посмотреть полностью ->"]]
    [:p {:class "text-sm opacity-60 italic"}
     "Завтра — новый день. То, что помогало раньше, поможет снова."]]])

;; ──────────────────────────────────────────────────────────────
;; 3. Только состояние: сводка есть, но релевантного инсайта нет
;; ──────────────────────────────────────────────────────────────
;; Редкий сценарий (сегодня записался, но инсайтов под это состояние нет).
;; Показываем облегчённый вариант без карточки инсайта — не «пустое место»,
;; а простое поддерживающее резюме. Не онбординг (сюда не просим создавать).

(def evening-summary-banner-no-insight
  [:section {:class "alert alert-info shadow-sm mb-4"}
   [:div {:class "flex items-start gap-3 w-full"}
    [:span {:class "text-sm font-medium flex-1"}
     "Сводка на завтра готова. Сегодня ты в состоянии «тревога»"]
    [:p {:class "text-sm opacity-70"}
     "Завтра — новый день. То, что помогало раньше, поможет снова."]]])

;; ──────────────────────────────────────────────────────────────
;; 4. Заметки по UX
;; ──────────────────────────────────────────────────────────────
;; - Размещение: САМЫЙ верх /feed, до заголовка «Лента». Сводка готовит к
;;   следующему дню — это первое, что видит пользователь вечером.
;; - alert-info: нейтральная поддержка, не похвала (success) и не тревога
;;   (error). Не стимулирует.
;; - Свёрнутое состояние — минимум: одна строка + две кнопки. Раскрытие по
;;   тапу (hyperscript toggle), не по отдельной странице (не плодить IA).
;; - Поддерживающий copy без вины: «Завтра — новый день. Сто, что помогало
;;   раньше, поможет снова». Запрещено: «цепочка разорвана», «ты пропустил»,
;;   любой strik/self-blame язык.
;; - Dismiss на сегодня: крестик -> POST /notifications/summary-dismiss ->
;;   last_summary_date=сегодня -> баннер исчезает. Через htmx, с CSRF.
;; - Контент раскрытия: state_label + релевантный инсайт (context + 1 advice)
;;   + напутствие. Не «все советы» — минимум нагрузки.
;; - Мягкий тон при плохом состоянии: акцент на совете («помогло»), не на
;;   фиксации боли. Карточка инсайта подписана «В прошлый раз в тревоге тебе
;;   помогало» — поддерживающий язык.
;; - Нет релевантного инсайта: облегчённый вариант, без онбординга.

;; ──────────────────────────────────────────────────────────────
;; 5. Touch-targets
;; ──────────────────────────────────────────────────────────────
;; - Кнопки «посмотреть» и «х»: h-11 min-h-11 (44px).
;; - Раскрытие по тапу на строке: вся строка — большая зона касания.
;; - Ссылка «посмотреть полностью» — text link (не первичное действие).