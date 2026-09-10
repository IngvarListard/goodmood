(ns app.views.entries
  (:require [app.domains.entries :as domains]
            [app.i18n :as i18n]
            [app.icons :as icons]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]
            [clojure.string :as str]))

(defn item
  "Отрендерить одну карточку записи (HTML-фрагмент для htmx-ответа POST /entries).
   Ожидает map с ключами :id, :date, :mood-score, :energy, :anxiety, :sleep-hours,
   :note, :activity."
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

(defn error-fragment
  "Мягкое сообщение об ошибке валидации (alert-warning, не alert-error)."
  [messages]
  [:div {:role "alert" :class "alert alert-warning"}
   [:span (clojure.string/join "; " messages)]])

;; ──────────────────────────────────────────────────────────────
;; Локальные хелперы ленты-стиля (state-бейдж, время, день) — паттерн
;; views/feed, но без утягивания его тяжёлых зависимостей (design D4).
;; ──────────────────────────────────────────────────────────────

(def ^:private danger-states
  #{"low" "anxiety"})

(def ^:private ok-states
  #{"ok" "calm"})

(defn- state-kind
  "Категория state_label записи: :danger / :ok / :neutral (Decision 2)."
  [entry]
  (let [raw (some-> entry :state-label (str/replace #"^state/" ""))]
    (cond
      (contains? danger-states raw) :danger
      (contains? ok-states raw) :ok
      :else :neutral)))

(defn- state-badge-class
  "CSS-класс state-бейджа записи по state_label."
  [entry]
  (case (state-kind entry)
    :danger "gm-badge-danger"
    :ok "gm-badge-ok"
    :neutral "bg-base-300 text-base-content"))

(defn- entry-label
  "Локализованный ярлык состояния записи (как в ленте): raw ключ / свободный
   текст / rule-based из осей."
  [entry]
  (let [raw (:state-label entry)
        key (when (and raw (not (str/starts-with? raw "state/"))
                       (not (str/blank? raw)))
              (keyword "state" raw))
        label-kw (or key (some-> raw keyword) (domains/state-label entry))
        translated (i18n/t label-kw)]
    (if (string? translated) translated (or raw (name label-kw)))))

(defn- format-time
  "Время «HH:MM» из created_at (SQLite datetime)."
  [created-at]
  (when created-at
    (let [parts (str/split created-at #"[ T]")]
      (when (>= (count parts) 2)
        (subs (second parts) 0 5)))))

(defn- month-name
  "Русское/английское название месяца по номеру (1-12)."
  [month-num]
  (i18n/t (keyword (str "feed/month-" month-num))))

(defn- day-header
  "Локализованный заголовок дня: сегодня / вчера / «21 августа» (как в ленте)."
  [date-str]
  (let [today (java.time.LocalDate/now)
        date (java.time.LocalDate/parse date-str)]
    (cond
      (= date today) (i18n/t :feed/today)
      (= date (.minusDays today 1)) (i18n/t :feed/yesterday)
      :else (str (.getDayOfMonth date) " " (month-name (.getMonthValue date))))))

(defn- axes-line
  "Строка «энергия 4 · тревога 7 · фокус 3»."
  [{:keys [energy anxiety focus]}]
  (str (i18n/t :entries/energy) " " (or energy "-")
       " · " (i18n/t :entries/anxiety) " " (or anxiety "-")
       " · " (i18n/t :entries/focus) " " (or focus "-")))

(def state-labels
  "Список state_label для select (без :state/ префикса — raw ключи)."
  ["mixed" "anxiety" "elevated" "low" "balanced" "neutral"])

;; ──────────────────────────────────────────────────────────────
;; Список /entries
;; ──────────────────────────────────────────────────────────────

(defn- list-card
  "Карточка записи в списке: state-бейдж, время, настроение и ядро осей;
   вся карточка — ссылка на /entries/:id."
  [entry]
  [:a {:href (str "/entries/" (:id entry))
       :class "block card bg-base-200 shadow-sm hover:border-primary/40 transition-colors"}
   [:div {:class "card-body p-3"}
    [:div {:class "flex items-center justify-between"}
     [:span {:class (str (state-badge-class entry) " text-[13px]")}
      (entry-label entry)]
     [:span {:class "text-sm text-base-content/60 tabular-nums"}
      (format-time (:created-at entry))]]
    [:p {:class "text-sm text-base-content/85 mt-1"}
     (i18n/t :entries/mood-label {:score (or (:mood-score entry) "-")})]
    [:p {:class "text-[13px] text-base-content/60"} (axes-line entry)]]])

(defn- day-section
  "Один день списка: заголовок дня + карточки записей дня."
  [date day-entries]
  [:section {:class "mb-5"}
   [:h2 {:class "text-sm font-medium text-base-content/60 mb-2 uppercase tracking-wide"}
    (day-header date)]
   [:div {:class "space-y-2"}
    (map list-card day-entries)]])

(defn- list-empty
  "Пустой список записей: подсказка и кнопка на чек-ин."
  []
  [:div {:class "card p-6 text-center"}
   [:p {:class "text-base-content/60"} (i18n/t :entries/empty)]
   [:a {:href "/check-in" :class "btn btn-primary mt-4"} (i18n/t :feed/go-check-in)]])

(defn list-page
  "Страница /entries: все записи пользователя, сгруппированные по дням
   (свежие сверху — entries приходят date desc, created_at desc)."
  [request entries]
  (let [grouped (group-by :date entries)
        dates (distinct (map :date entries))]
    (layout/layout
     {:title (i18n/t :entries/list)
      :active :feed
      :request request}
     navigation/nav-items
     [:div {:id "entries-page"}
      [:div {:class "card bg-base-200 shadow-sm mb-4"}
       [:div {:class "card-body p-4"}
        [:div {:class "flex items-center gap-3"}
         [:a {:href "/feed"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
              :aria-label (i18n/t :entries/back)}
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 :width 20 :height 20 :viewBox "0 0 24 24"
                 :fill "none" :stroke "currentColor"
                 :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
           [:path {:d "M15 18l-6-6 6-6"}]]]
         [:h1 {:class "text-2xl font-bold"} (i18n/t :entries/list)]
         [:span {:class "text-sm text-base-content/60 ml-auto tabular-nums"}
          (i18n/t :entries/count-plural {:n (count entries)})]]]]
      (if (seq entries)
        (for [date dates]
          (day-section date (get grouped date)))
        (list-empty))])))

;; ──────────────────────────────────────────────────────────────
;; Карточка /entries/:id — read + edit + delete
;; ──────────────────────────────────────────────────────────────

(defn- range-field
  "Слайдер ядра 0–10 с live-значением (паттерн чек-ина), предзаполнен."
  [label name field-id value]
  (let [v (or value 5)]
    [:div {:class "form-control mb-4"}
     [:div {:class "label px-0"}
      [:span {:class "label-text text-base font-medium"} label]]
     [:input {:type "range" :name name :min "0" :max "10" :value v
              :class "gm-range"
              :_ (str "on input put my value into #" field-id "-value")}]
     [:div {:class "flex justify-between mt-0.5 px-0.5"}
      [:span {:class "text-xs text-base-content/60"} "0"]
      [:span {:id (str field-id "-value") :class "text-base font-bold tabular-nums"} v]
      [:span {:class "text-xs text-base-content/60"} "10"]]]))

(defn- state-label-field
  "Select ярлыка состояния: пусто (—) = очистить, иначе стандартные raw ключи."
  [entry]
  (let [raw-key (some-> entry :state-label (str/replace #"^state/" ""))]
    [:div {:class "form-control mb-4"}
     [:label {:class "label px-0 pb-1"}
      [:span {:class "label-text text-base font-medium"}
       (i18n/t :entries/state-label)]]
     [:select {:name "state_label" :class "select select-bordered w-full"}
      [:option {:value "" :selected (str/blank? raw-key)} "—"]
      (for [sl state-labels]
        [:option {:value sl :selected (= sl raw-key)}
         (i18n/t (keyword "state" sl))])]]))

(defn- edit-form
  "Edit-фрагмент карточки (скрытый до «Править»): ядро-слайдеры, дата, сон,
   ярлык состояния, заметка, активность. Один hx-post на /entries/:id
   (CSRF в hx-headers, json-enc)."
  [csrf entry]
  (let [id (:id entry)]
    [:div {:id (str "entry-edit-" id) :class "hidden"}
     [:form {:hx-post (str "/entries/" id)
             :hx-ext "json-enc"
             :hx-headers (str "{\"X-CSRF-Token\": \"" csrf "\"}")
             :hx-target (str "#entry-form-error-" id)
             :hx-swap "innerHTML"}
      (range-field (i18n/t :entries/mood) "mood_score" "mood" (:mood-score entry))
      (range-field (i18n/t :entries/energy) "energy" "energy" (:energy entry))
      (range-field (i18n/t :entries/anxiety) "anxiety" "anxiety" (:anxiety entry))
      (range-field (i18n/t :entries/focus) "focus" "focus" (:focus entry))
      [:div {:class "form-control mb-4"}
       [:label {:class "label px-0 pb-1"}
        [:span {:class "label-text text-base font-medium"}
         (i18n/t :entries/date-label)]]
       [:input {:type "date" :name "date" :value (:date entry)
                :class "input input-bordered w-full"}]]
      [:div {:class "form-control mb-4"}
       [:label {:class "label px-0 pb-1"}
        [:span {:class "label-text text-base font-medium"}
         (i18n/t :entries/sleep)]]
       [:input {:type "number" :name "sleep_hours" :step "0.1" :min "0" :max "24"
                :value (or (some-> entry :sleep-hours str) "")
                :class "input input-bordered w-full"}]]
      (state-label-field entry)
      [:div {:class "form-control mb-4"}
       [:label {:class "label px-0 pb-1"}
        [:span {:class "label-text text-base font-medium"}
         (i18n/t :entries/note)]]
       [:textarea {:name "note" :rows "3" :maxlength "500"
                   :class "textarea textarea-bordered w-full"}
        (or (:note entry) "")]]
      [:div {:class "form-control mb-4"}
       [:label {:class "label px-0 pb-1"}
        [:span {:class "label-text text-base font-medium"}
         (i18n/t :entries/activity)]]
       [:input {:type "text" :name "activity" :maxlength "200"
                :value (or (:activity entry) "")
                :class "input input-bordered w-full"}]]
      [:div {:id (str "entry-form-error-" id)}]
      [:div {:class "flex gap-2 mt-2"}
       [:button {:type "submit"
                 :class "btn btn-primary flex-1 h-12 rounded-2xl"}
        (i18n/t :entries/save-changes)]
       [:button {:type "button" :class "btn btn-ghost h-12 rounded-2xl"
                 :_ (str "on click add .hidden to #entry-edit-" id
                         " then remove .hidden from #entry-read-" id)}
        (i18n/t :entries/cancel)]]]]))

(defn- read-view
  "Read-блоки карточки: бейдж/время/дата, ядро, сон, заметка, активность
   + кнопки «Править» и «Удалить»."
  [entry]
  (let [id (:id entry)]
    [:div {:id (str "entry-read-" id)}
     [:div {:class "flex items-center justify-between flex-wrap gap-2"}
      [:div {:class "flex items-center gap-2"}
       [:span {:class (str (state-badge-class entry) " text-[13px]")}
        (entry-label entry)]
       [:span {:class "text-sm text-base-content/60 tabular-nums"}
        (or (format-time (:created-at entry)) "")]]
      [:span {:class "text-sm text-base-content/60 tabular-nums"}
       (str (i18n/t :entries/date-label) ": " (:date entry))]]
     [:p {:class "text-lg font-semibold mt-3"}
      (i18n/t :entries/mood-label {:score (or (:mood-score entry) "-")})]
     [:p {:class "text-sm text-base-content/85"} (axes-line entry)]
     (when (some? (:sleep-hours entry))
       [:p {:class "text-sm text-base-content/85"}
        (str (i18n/t :entries/sleep-label)
             (format "%.1f" (double (:sleep-hours entry))))])
     (when (seq (:note entry))
       [:p {:class "text-sm text-base-content/80 mt-2"} (:note entry)])
     (when (seq (:activity entry))
       [:p {:class "text-sm text-base-content/80 mt-2"}
        (str (i18n/t :entries/activity-label) (:activity entry))])
     [:div {:class "flex items-center gap-2 mt-4"}
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                :_ (str "on click add .hidden to #entry-read-" id
                        " then remove .hidden from #entry-edit-" id)}
       (i18n/t :entries/edit)]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-3 text-error"
                :aria-label (i18n/t :entries/delete)
                ;; Нативный dialog API: showModal/close — Esc и backdrop
                ;; закрываются без лишних классов (паттерн medications).
                :_ (str "on click call #del-entry-" id ".showModal()")}
       (i18n/t :entries/delete)]]]))

(defn- delete-modal
  "Confirm-модалка удаления (паттерн инсайтов: dialog + hx-delete + CSRF,
   после удаления — редирект на /entries)."
  [csrf entry]
  (let [id (:id entry)]
    [:dialog {:id (str "del-entry-" id) :class "modal modal-bottom sm:modal-middle"}
     [:div {:class "modal-box"}
      [:h3 {:class "text-lg font-bold mb-2"} (i18n/t :entries/delete-confirm)]
      [:p {:class "text-sm text-base-content/70 mb-4"} (i18n/t :entries/delete-confirm-text)]
      [:div {:class "modal-action"}
       [:form {:method "dialog"}
        [:button {:class "btn btn-ghost h-11 min-h-11"} (i18n/t :entries/cancel)]]
       [:button {:class "btn btn-error h-11 min-h-11"
                 :hx-delete (str "/entries/" id)
                 :hx-headers (str "{\"X-CSRF-Token\": \"" csrf "\"}")}
        (i18n/t :entries/delete-action)]]]
     [:form {:method "dialog" :class "modal-backdrop"}
      [:button "close"]]]))

(defn- entry-card
  "Карточка записи: read-блоки (видимые) + edit-форма (скрытая) + div ошибки
   валидации. edit-кнопка переключает div'ы hyperscript'ом."
  [csrf entry]
  [:div {:id (str "entry-" (:id entry))
         :class "card bg-base-200 shadow-sm mb-4"}
   [:div {:class "card-body p-4"}
    (read-view entry)
    (edit-form csrf entry)]])

(defn card-oob
  "Карточка с hx-swap-oob (outerHTML) — ответ POST /entries/:id: свапает
   карточку на свежую read-версию, edit-форма закрывается."
  [csrf entry]
  (assoc-in (entry-card csrf entry)
            [1 :hx-swap-oob] (str "outerHTML:#entry-" (:id entry))))

(defn show-page
  "Страница /entries/:id: шапка (назад + бейдж состояния) и карточка
   записи с in-place edit и confirm-модалкой удаления."
  [request entry]
  (let [csrf-token (:anti-forgery-token request)]
    (layout/layout
     {:title (i18n/t :entries/list)
      :active :feed
      :request request}
     navigation/nav-items
     [:div {:id "entry-page"}
      [:div {:class "card bg-base-200 shadow-sm mb-4"}
       [:div {:class "card-body p-4"}
        [:div {:class "flex items-center gap-3"}
         [:a {:href "/entries"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
              :aria-label (i18n/t :entries/back-to-list)}
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 :width 20 :height 20 :viewBox "0 0 24 24"
                 :fill "none" :stroke "currentColor"
                 :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
           [:path {:d "M15 18l-6-6 6-6"}]]]
         [:h1 {:class "text-2xl font-bold"} (i18n/t :entries/list)]
         [:span {:class (str (state-badge-class entry) " text-[13px] ml-auto")}
          (entry-label entry)]]]]
      (entry-card csrf-token entry)
      (delete-modal csrf-token entry)])))
