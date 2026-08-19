(ns app.views.entries
  (:require [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]))

(defn- value-row
  "Отрендерить подпись значения слайдера: минимум, текущее значение, максимум."
  [value-id]
  [:div {:class "flex justify-between mt-0.5 px-0.5"}
   [:span {:class "text-xs opacity-40"} "0"]
   [:span {:id value-id :class "text-sm font-semibold tabular-nums"} "5"]
   [:span {:class "text-xs opacity-40"} "10"]])

(defn- range-field
  "Отрендерить range-поле ядра: label, слайдер с отображением текущего значения.
   field-id: суффикс id для value-спана, name: имя поля формы."
  [label field-id name color]
  [:div {:class "form-control mb-5"}
   [:div {:class "label px-0"}
    [:span {:class "label-text text-base font-medium"} label]]
   [:input {:type "range" :name name :min "0" :max "10" :value "5"
            :class (str "range " color)
            :style "height: 2rem"
            :_ (str "on input put my value into #" field-id "-value")}]
   (value-row (str field-id "-value"))])

(defn- template-tabs
  "Отрендерить переключатель шаблонов (утро/день/вечер/событие).
   Активный таб подсвечивается, выбранный шаблон пишется в скрытое поле template."
  []
  [:div {:class "tabs tabs-boxed mb-4"}
   (map (fn [{:keys [key label active]}]
          [:button {:type "button"
                    :class (str "tab" (when active " tab-active"))
                    :data-template key
                    :role "tab"
                    :_ "on click
                          remove .tab-active from .tab
                          add .tab-active to me
                          set #template-value.value to @data-template"}
           label])
        [{:key "morning" :label (i18n/t :template/morning) :active true}
         {:key "day" :label (i18n/t :template/day)}
         {:key "evening" :label (i18n/t :template/evening)}
         {:key "event" :label (i18n/t :template/event)}])
   [:input {:type "hidden" :name "template" :id "template-value" :value "morning"}]])

(defn- optional-block
  "Отрендерить опциональный коллапс-блок: чекбокс-переключатель, заголовок и контент.
   field-id: id инпута внутри блока (он передаётся disabled/read по состоянию блока),
   content: вектор hiccup-контента блока."
  [label field-id content]
  [:div {:class "collapse collapse-arrow bg-base-300/50 mb-2 rounded-lg"}
   [:input {:type "checkbox"
            :_ (str "on change
                       if me.checked
                         remove [disabled] from #" field-id "
                       else
                         add [disabled] to #" field-id)}]
   [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"} label]
   [:div {:class "collapse-content"} content]])

(defn form
  "Сформировать HTMX-форму создания записи (mobile-first, тёмная тема).
   Ядро: mood_score / energy / anxiety (range 0-10).
   Опционально: focus, sleep_hours, note, activity (collapse-блоки).
   Отправляет JSON на /entries, ответ направляет в #form-error.
   При успехе — сброс формы и вставка записи в #entries-list (OOB)."
  []
  [:form {:hx-post "/entries"
          :hx-ext "json-enc"
          :hx-target "#form-error"
          :hx-swap "innerHTML"
          :_ "on htmx:afterRequest
                if event.detail.successful
                  me.reset()
                  for el in <[data-optional]/> in me
                    add [disabled] to el
                  end
                end"
          :class "card bg-base-200 p-4"}
   [:h2 {:class "text-lg font-semibold mb-1"} (i18n/t :entries/new-entry)]
   [:p {:class "text-sm opacity-60 mb-4"} (i18n/t :entries/how-are-you)]
   (template-tabs)
   [:div {:id "form-error" :class "mb-3"}]
   [:div {:class "grid grid-cols-1 gap-3"}
    (range-field (i18n/t :entries/mood) "mood" "mood_score" "range-primary")
    (range-field (i18n/t :entries/energy) "energy" "energy" "range-success")
    (range-field (i18n/t :entries/anxiety) "anxiety" "anxiety" "range-warning")]
   [:div {:class "border-t border-base-300 pt-4 mt-2"}
    [:p {:class "text-xs opacity-50 mb-3 tracking-wide uppercase"}
     (i18n/t :entries/optional)]
    (optional-block (i18n/t :entries/focus) "focus"
     [:div {:class "form-control"}
      [:input {:type "range" :name "focus" :min "0" :max "10" :value "5"
               :id "focus" :data-optional true :disabled true
               :class "range range-info" :style "height: 2rem"
               :_ "on input set #focus-value.textContent to my.value"}]
      (value-row "focus-value")])
    (optional-block (i18n/t :entries/sleep) "sleep_hours"
     [:input {:type "number" :name "sleep_hours" :id "sleep_hours"
              :data-optional true :disabled true
              :step "0.1" :min "0" :max "24"
              :placeholder "7.5"
              :class "input input-bordered w-full"}])
    (optional-block (i18n/t :entries/note) "note"
     [:textarea {:name "note" :id "note" :rows "3" :maxlength "500"
                 :data-optional true :disabled true
                 :placeholder "..."
                 :class "textarea textarea-bordered w-full"}])
    (optional-block (i18n/t :entries/activity) "activity"
     [:input {:type "text" :name "activity" :id "activity"
              :data-optional true :disabled true
              :placeholder "..."
              :class "input input-bordered w-full"}])]
   [:button {:type "submit" :class "btn btn-primary w-full h-12 mt-4"}
    (i18n/t :entries/save)]])

(defn item
  "Отрендерить одну карточку записи. Ожидает map с ключами :id, :date,
   :mood-score, :energy, :anxiety, :sleep-hours, :note, :activity."
  [{:keys [id date mood-score energy anxiety sleep-hours note activity]}]
  [:li {:id (str "entry-" id)
        :hx-swap-oob (str "afterbegin:#entries-list")
        :class "card bg-base-200 p-4"}
   [:div {:class "flex items-center justify-between gap-2 flex-wrap"}
    [:span {:class "text-lg font-semibold"}
     (i18n/t :entries/mood-label {:score (or mood-score "-")})]
    [:span {:class "text-sm opacity-70"} (or date "")]]
   [:div {:class "flex flex-wrap gap-3 mt-2 text-sm opacity-80"}
    (when (some? energy)
      [:span (i18n/t :entries/energy-label) (str energy "/10")])
    (when (some? anxiety)
      [:span (i18n/t :entries/anxiety-label) (str anxiety "/10")])]
   (when (some? sleep-hours)
     [:p [:span {:class "font-medium"} (i18n/t :entries/sleep-label)]
      (format "%.1f ч" (double sleep-hours))])
   (when (seq note)
    [:p {:class "mt-2"} note])
   (when (seq activity)
    [:p {:class "mt-2"}
     [:span {:class "font-medium"} (i18n/t :entries/activity-label)]
     activity])])

(defn entries-list
  "Отрендерить список записей или сообщение о пустом списке."
  [entries]
  (if (seq entries)
    [:ul {:id "entries-list" :class "space-y-3"}
     (map item entries)]
    [:div
     [:ul {:id "entries-list" :class "space-y-3"}]
     [:p {:class "text-center opacity-60 py-8"} (i18n/t :entries/empty)]]))

(defn error-fragment
  "Мягкое сообщение об ошибке валидации (alert-warning, не alert-error)."
  [messages]
  [:div {:role "alert" :class "alert alert-warning"}
   [:span (clojure.string/join "; " messages)]])

(defn page
  "Отрендерить полную страницу записей: заголовок, форму, контейнер ошибок,
   список записей и лейаут."
  [request entries]
  (let [content [:div {:class "max-w-2xl mx-auto p-4"}
                 [:h1 {:class "text-2xl font-bold mb-1"} (i18n/t :entries/title)]
                 [:p {:class "text-sm opacity-70 mb-6"} (i18n/t :app/description)]
                 (form)
                 [:div {:id "form-error" :class "mt-3"}]
                 [:h2 {:class "text-xl font-semibold mt-8 mb-3"} (i18n/t :entries/list)]
                 (entries-list entries)]]
    (layout/layout {:title (i18n/t :entries/title)
                    :request request}
                   navigation/nav-items
                   content)))