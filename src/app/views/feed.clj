(ns app.views.feed
  (:require [app.domains.entries :as domains]
            [app.i18n :as i18n]
            [app.icons :as icons]
            [app.views.ai :as ai]
            [app.views.insights :as insights]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]
            [app.views.notifications :as notifications]
            [app.views.state-periods :as sp]
            [cheshire.core :as json]
            [clojure.math :as math]
            [clojure.string :as str]))

;; State-маппинг: визуальная семантика (Decision 2), не i18n.
(def ^:private danger-states
  #{"low" "anxiety"})

(def ^:private ok-states
  #{"ok" "calm"})

(defn- state-kind
  "Категория state_label записи: :danger / :ok / :neutral (Decision 2).
   Префикс 'state/' отбрасывается; свободный текст AI-ярлыка или nil —
   нейтральный."
  [entry]
  (let [raw (some-> entry :state-label (str/replace #"^state/" ""))]
    (cond
      (contains? danger-states raw) :danger
      (contains? ok-states raw) :ok
      :else :neutral)))

(defn- state-badge-class
  "CSS-класс state-бейджа записи по state_label (Decision 2):
   low/anxiety — gm-badge-danger, ok/calm — gm-badge-ok,
   свободный текст AI-ярлыка или nil — нейтральный."
  [entry]
  (case (state-kind entry)
    :danger "gm-badge-danger"
    :ok "gm-badge-ok"
    :neutral "bg-base-300 text-base-content"))

(defn- day-dot-class
  "Цвет точки дня на timeline-линии (Decision 1): danger — bg-error,
   ok — bg-success, иначе нейтральный."
  [entry]
  (case (state-kind entry)
    :danger "bg-error"
    :ok "bg-success"
    :neutral "bg-base-content/40"))

(defn- metric-chip
  "Чип метрики из макета lenta: иконка в цвете CSS-переменной var-name
   (--gm-metric-*), подпись и значение (или «–» при отсутствии).
   label-kw — i18n-ключ подписи (:entries/energy и т.п.),
   icon — имя heroicon в app.icons."
  [label-kw icon var-name value]
  [:div {:class "gm-chip p-2.5 flex items-center gap-2 min-w-0"}
   [:span {:class "shrink-0" :style {:color (str "var(" var-name ")")}}
    (icons/svg icon)]
   [:div {:class "min-w-0"}
    [:div {:class "text-[11px] text-base-content/60 truncate"} (i18n/t label-kw)]
    [:div {:class "text-[15px] truncate"} (or value "–")]]])

(defn- entry-label
  "Вернуть локализованный ярлык состояния записи.
   state_label хранится как 'state/<key>' или '<key>' (raw); свободный текст
   AI-предложения («тревожный интроверт») отображается как есть."
  [entry]
  (let [raw (:state-label entry)
        key (when (and raw (not (str/starts-with? raw "state/")) (not (str/blank? raw)))
              (keyword "state" raw))
        label-kw (or key (some-> raw keyword) (domains/state-label entry))
        translated (i18n/t label-kw)]
    (if (string? translated) translated (or raw (name label-kw)))))

(defn- format-time
  "Извлечь время «HH:MM» из created_at (формат SQLite datetime('now'))."
  [created-at]
  (when created-at
    (let [parts (str/split created-at #"[ T]")]
      (when (>= (count parts) 2)
        (subs (second parts) 0 5)))))

(defn- month-name
  "Русское название месяца по номеру (1-12) через i18n."
  [month-num]
  (i18n/t (keyword (str "feed/month-" month-num))))

(defn- day-header
  "Локализованный заголовок дня: сегодня / вчера / «21 августа»."
  [date-str]
  (let [today (java.time.LocalDate/now)
        date (java.time.LocalDate/parse date-str)]
    (cond
      (= date today) (i18n/t :feed/today)
      (= date (.minusDays today 1)) (i18n/t :feed/yesterday)
      :else (str (.getDayOfMonth date) " " (month-name (.getMonthValue date))))))

;; ──────────────────────────────────────────────────────────────
;; График недели возле AI-секций (Decision 4, макет lenta 95–104):
;; настроение за последние 7 дней, точки красятся по порогам
;; ──────────────────────────────────────────────────────────────

(def ^:private chart-mid-y
  "Y средней линии viewBox 0 0 300 34 для дней без данных."
  18)

(defn- week-dates
  "Окно графика: последние 7 календарных дней, заканчивая сегодня."
  []
  (let [today (java.time.LocalDate/now)]
    (vec (map #(.minusDays today %) (range 6 -1 -1)))))

(defn- mood-by-day
  "Настроение по каждой дате окна: mood_score последней записи дня
   (entries отсортированы date desc, created_at desc); нет записи — nil."
  [dates entries]
  (let [grouped (group-by :date entries)]
    (mapv #(some-> (first (get grouped (str %))) :mood-score) dates)))

(defn- chart-point-x
  "X-координата i-й точки в viewBox 0 0 300 34: 10 + i*(280/6)."
  [i]
  (int (+ 10 (* i (/ 280 6)))))

(defn- mood-point-y
  "Y-координата точки по значению 0..10: 0 внизу (y=30), 10 вверху (y=6)."
  [v]
  (math/round (- 30 (* (/ v 10.0) 24))))

(defn- mood-fill-style
  "Inline-заливка точки по порогам задачи 5: ≤3 — success, 4–6 — warning,
   ≥7 — error. Явные CSS-переменные вместо daisyUI fill-* (их не генерирует
   Tailwind browser CDN — точки рендерились чёрными, design D9)."
  [v]
  (cond
    (<= v 3) {:fill "var(--color-success)"}
    (<= v 6) {:fill "var(--color-warning)"}
    :else {:fill "var(--color-error)"}))

(defn- week-range-label
  "Диапазон недели слева от графика (как в макете «9 — 15 мая»); при
   переходе месяца — «30 августа — 5 сентября»."
  [dates]
  (let [first-date (first dates)
        last-date (peek dates)
        d1 (.getDayOfMonth first-date)
        d2 (.getDayOfMonth last-date)
        m1 (.getMonthValue first-date)
        m2 (.getMonthValue last-date)]
    (if (= m1 m2)
      (str d1 " — " d2 " " (month-name m2))
      (str d1 " " (month-name m1) " — " d2 " " (month-name m2)))))

(defn- week-chart
  "График настроения за последние 7 дней (макет lenta 92–106): слева
   диапазон дат, справа SVG viewBox 0 0 300 34 — polyline через точки
   с данными и круги r=6 (цвет по порогам), день без данных — контурный
   круг на средней высоте; ниже — подписи Пн…Вс. Меньше 2 дней с данными
   — nil (вызывающий код показывает fallback-текст)."
  [entries]
  (let [dates (week-dates)
        moods (mood-by-day dates entries)
        pts (keep-indexed (fn [i v]
                            (when (some? v)
                              {:x (chart-point-x i)
                               :y (mood-point-y v)}))
                          moods)]
    (when (>= (count pts) 2)
      (let [pts-str (->> pts
                         (map #(str (:x %) "," (:y %)))
                         (str/join " "))]
        [:div {:class "flex items-center gap-4"}
         [:span {:class "text-[13px] text-base-content/60 w-[86px] shrink-0"}
          (week-range-label dates)]
         [:div {:class "flex-1 min-w-0"}
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 :viewBox "0 0 300 34" :class "w-full h-[34px]"}
           [:polyline {:points pts-str
                       :style {:stroke "var(--color-primary)"}
                       :fill "none" :stroke-width 2}]
           (map-indexed (fn [i v]
                          (if (some? v)
                            [:circle {:cx (chart-point-x i)
                                      :cy (mood-point-y v) :r 6
                                      :style (mood-fill-style v)}]
                            [:circle {:cx (chart-point-x i)
                                      :cy chart-mid-y :r 6
                                      :class "fill-none stroke-base-content/40 stroke-2"}]))
                        moods)]
          [:div {:class "flex justify-between text-[11px] text-base-content/60 px-[2px] -mt-1"}
           (for [n (range 1 8)]
             ^{:key n}
             [:span (i18n/t (keyword "feed" (str "wd-" n)))])]]]))))

(defn- week-chart-section
  "Секция графика недели возле AI-секций (показывается только когда AI
   включён): график или muted fallback-текст при недостатке данных."
  [entries]
  [:section {:class "mb-4"}
   (if-let [chart (week-chart entries)]
     chart
     [:p {:class "text-[13px] text-base-content/60"} (i18n/t :ai/no-week-data)])])

(defn- axes-line
  "Строка «энергия 4 · тревога 7 · фокус 3» для карточек."
  [{:keys [energy anxiety focus]}]
  (str (i18n/t :entries/energy) " " (or energy "-")
       " · " (i18n/t :entries/anxiety) " " (or anxiety "-")
       " · " (i18n/t :entries/focus) " " (or focus "-")))

(defn- feed-delete-modal
  "Confirm-модалка удаления карточки ленты (design D2): hx-delete с
   ?from=feed убирает карточку #feed-entry-<id> in-place (hx-swap delete)."
  [csrf entry]
  (let [id (:id entry)]
    [:dialog {:id (str "del-feed-entry-" id)
              :class "modal modal-bottom sm:modal-middle"}
     [:div {:class "modal-box"}
      [:h3 {:class "text-lg font-bold mb-2"} (i18n/t :entries/delete-confirm)]
      [:p {:class "text-sm text-base-content/70 mb-4"}
       (i18n/t :entries/delete-confirm-text)]
      [:div {:class "modal-action"}
       [:form {:method "dialog"}
        [:button {:class "btn btn-ghost h-11 min-h-11"}
         (i18n/t :entries/cancel)]]
       [:button {:class "btn btn-error h-11 min-h-11"
                 :hx-delete (str "/entries/" id "?from=feed")
                 :hx-headers (str "{\"X-CSRF-Token\": \"" csrf "\"}")
                 :hx-target (str "#feed-entry-" id)
                 :hx-swap "delete"}
        (i18n/t :entries/delete-action)]]]
     [:form {:method "dialog" :class "modal-backdrop"}
      [:button "close"]]]))

(defn- entry-card
  "Карточка записи в timeline: state-бейдж, время и меню действий справа;
   ниже — grid-cols-2 чипов метрик (четыре оси), отсутствующее значение — «–».
   Меню — daisyUI dropdown (правка/удаление, design D1); id #feed-entry-<id> —
   цель in-place удаления (design D2)."
  [csrf {:keys [created-at id] :as entry}]
  [:div {:id (str "feed-entry-" id) :class "card bg-base-200 shadow-sm"}
   [:div {:class "card-body p-3"}
    [:div {:class "flex items-center justify-between gap-2"}
     [:span {:class (str (state-badge-class entry) " min-w-0 flex-1 truncate text-[13px]")}
      (entry-label entry)]
     [:div {:class "flex items-center gap-3 text-sm text-base-content/60 shrink-0"}
      [:span {:class "tabular-nums"} (format-time created-at)]
      [:div {:class "dropdown dropdown-end"
             :tabindex "0" :role "button"
             :aria-label (i18n/t :entries/actions-menu)}
       (icons/svg "ellipsis-horizontal" {:class "text-base-content/40"})
       [:ul {:tabindex "0"
             :class "dropdown-content menu bg-base-100 rounded-box z-[1] w-40 p-2 shadow"}
        [:li [:a {:href (str "/entries/" id)} (i18n/t :entries/edit)]]
        [:li [:button {:type "button"
                       :_ (str "on click call #del-feed-entry-" id ".showModal()")}
              (i18n/t :entries/delete)]]]]]]
    [:div {:class "grid grid-cols-2 gap-2 mt-3"}
     (metric-chip :entries/energy "bolt" "--gm-metric-energy" (:energy entry))
     (metric-chip :entries/anxiety "fire" "--gm-metric-anxiety"
                  (:anxiety entry))
     (metric-chip :entries/focus "eye" "--gm-metric-focus" (:focus entry))
     (metric-chip :entries/aggression "hand-raised"
                  "--gm-metric-aggression" (:aggression entry))]]
   (feed-delete-modal csrf entry)])

(def ^:private chart-datasets
  "Датасеты графика: оси + составной последним (design D4/D8)."
  [{:key :mood-score :label-kw :entries/mood :color-var "--color-secondary"}
   {:key :energy :label-kw :entries/energy :color-var "--gm-metric-energy"}
   {:key :anxiety :label-kw :entries/anxiety :color-var "--gm-metric-anxiety"}
   {:key :focus :label-kw :entries/focus :color-var "--gm-metric-focus"}
   {:key :aggression :label-kw :entries/aggression
    :color-var "--gm-metric-aggression"}
   {:key :composite :label-kw :feed/overall-mood :color-var "--color-primary"}])

(defn- chart-payload
  "JSON-полезная нагрузка canvas: labels + datasets (составной последний)."
  [{:keys [dates axes composite]}]
  {:labels dates
   :datasets (mapv (fn [{:keys [key label-kw color-var]}]
                     {:key (name key)
                      :label (i18n/t label-kw)
                      :values (if (= key :composite) composite (get axes key))
                      :colorVar color-var})
                   chart-datasets)})

(defn- data-day-count
  "Число дней окна, где есть хотя бы одно значение любой оси."
  [{:keys [composite]}]
  (count (filter some? composite)))

(defn- chart-period-btn
  "Кнопка-переключатель периода графика: hx-get /feed/chart?period=…
   свапает контейнер #feed-chart (outerHTML); активная подсвечена и
   помечена aria-pressed."
  [current p label-kw]
  [:button {:class (if (= current p)
                     "btn btn-xs btn-primary"
                     "btn btn-xs btn-ghost")
            ;; строкой: hiccup рендерит boolean true как голый атрибут
            :aria-pressed (if (= current p) "true" "false")
            :hx-get (str "/feed/chart?period=" (name p))
            :hx-target "#feed-chart"
            :hx-swap "outerHTML"}
   (i18n/t label-kw)])

(defn- chart-period-switcher
  "Переключатель 3 дня / неделя / месяц (дефолт — неделя, design D5/D6)."
  [period]
  [:div {:class "flex justify-center gap-1 mt-1"}
   (chart-period-btn period :3d :feed/period-3d)
   (chart-period-btn period :week :feed/period-week)
   (chart-period-btn period :month :feed/period-month)])

(defn chart-fragment
  "Фрагмент линейного графика для hero-карточки /feed (design D3/D6/D10):
   canvas[data-gm-chart] 200×200 с кнопками периода или приглушённый
   fallback-текст при < 2 днях данных. series — daily-axis-series;
   period — :3d/:week/:month."
  [series period]
  [:div {:id "feed-chart" :class "shrink-0 mx-auto"}
   (if (< (data-day-count series) 2)
     [:div {:class "w-[200px] h-[200px] flex items-center justify-center px-3 text-center"}
      [:p {:class "text-[13px] text-base-content/60"}
       (i18n/t :feed/chart-no-data)]]
     [:canvas {:width 200 :height 200
               :role "img"
               :aria-label (i18n/t :feed/chart-aria)
               :data-gm-chart (json/generate-string (chart-payload series))}])
   (chart-period-switcher period)])

(defn- hero-card
  "Hero-карточка последней записи сегодня: график периода (контейнер
   #feed-chart) + метаданные записи. chart — фрагмент feed/chart-fragment."
  [{:keys [sleep-hours note created-at] :as entry} chart]
  [:article {:class "card bg-base-200 shadow-md"}
   [:div {:class "card-body p-4"}
    [:div {:class "flex flex-col sm:flex-row gap-4 items-start"}
     chart
     [:div {:class "flex-1 min-w-0 w-full"}
      [:div {:class "flex items-center justify-between gap-2"}
       [:span {:class (str (state-badge-class entry) " min-w-0 truncate text-[13px]")}
        (entry-label entry)]
       [:span {:class "text-sm text-base-content/60 tabular-nums shrink-0"}
        (format-time created-at)]]
      [:p {:class "text-sm text-base-content/85 truncate"} (axes-line entry)]
      (when sleep-hours
        [:p {:class "text-sm text-base-content/60 mt-1"}
         (str (i18n/t :entries/sleep) " " (layout/format-sleep sleep-hours))])
      (when (seq note)
        [:p {:class "text-sm text-base-content/80 mt-2 break-words"} note])]]]])

(defn- empty-state
  "Пустая секция «Сегодня» (макет lenta 51–72): SVG-иллюстрация — контур
   лица, глаза, улыбка и звёзды, stroke из var(--color-primary); заголовок
   «Как ты сегодня?», описание и кнопка создания записи на /check-in.
   Иллюстрация — hiccup-вектор без raw; заливки звёзд и точек — явный
   fill вместо унаследованного stroke."
  []
  [:div {:class "card p-6 text-center"}
   [:svg {:class "mx-auto"
          :xmlns "http://www.w3.org/2000/svg"
          :width 170 :height 150 :viewBox "0 0 170 150"
          :fill "none" :stroke "var(--color-primary)"}
    [:path {:d "M85 22c30 0 48 22 48 50s-20 52-48 52-48-24-48-52 18-50 48-50z"
            :stroke-width 3.5 :fill "none"}]
    [:path {:d "M68 62c3-6 9-6 12 0"
            :stroke-width 3.5 :stroke-linecap "round"}]
    [:path {:d "M90 62c3-6 9-6 12 0"
            :stroke-width 3.5 :stroke-linecap "round"}]
    [:path {:d "M68 84c6 10 28 10 34 0"
            :stroke-width 3.5 :stroke-linecap "round"}]
    [:g {:stroke-width 2.5 :stroke-linecap "round"}
     [:path {:d "M22 46l3-8 3 8 8 3-8 3-3 8-3-8-8-3z"
             :fill "var(--color-primary)" :stroke "none"}]
     [:path {:d "M144 84l3-8 3 8 8 3-8 3-3 8-3-8-8-3z"
             :fill "var(--color-primary)" :stroke "none"}]]
    [:circle {:cx 150 :cy 35 :r 3 :fill "none" :stroke-width 2}]
    [:circle {:cx 18 :cy 88 :r 3 :fill "var(--color-primary)" :stroke "none"}]
    [:circle {:cx 140 :cy 122 :r 3 :fill "var(--color-primary)" :stroke "none"}]]
   [:h2 {:class "text-2xl font-bold mt-1"} (i18n/t :feed/how-are-you)]
   [:p {:class "text-[14px] text-base-content/60 mt-2 leading-relaxed"}
    (i18n/t :feed/how-are-you-desc)]
   [:a {:href "/check-in"
        :class "btn btn-primary w-full h-14 rounded-2xl mt-5"}
    [:span {:class "w-8 h-8 rounded-full bg-white text-primary flex items-center justify-center"}
     [:svg {:xmlns "http://www.w3.org/2000/svg"
            :width 16 :height 16 :viewBox "0 0 24 24"
            :fill "none" :stroke "currentColor"
            :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
      [:path {:d "M12 5v14M5 12h14"}]]]
    (i18n/t :feed/go-check-in)]])

(defn- today-section
  "Секция «Сегодня»: hero-карточка последней записи + виджет инсайтов +
   карточки остальных записей дня, или онбординг при отсутствии записей.
   state-label: текущий state_label последней записи сегодня (для виджета).
   insight: 1 релевантный инсайт или nil.
   chart: фрагмент линейного графика периода (feed/chart-fragment).
   csrf: токен для модалок удаления карточек (feed-delete-modal).
   В мягком состоянии (low/mixed) виджет поднимается выше hero — акцент
   на совете, не на фиксации боли (Decision 14.4)."
  [today-entries state-label insight chart csrf & [ai-advice]]
  (let [soft? (and state-label (contains? #{"low" "mixed"} state-label))]
    [:section {:id (str "feed-day-" (java.time.LocalDate/now))
               :class "mb-6"}
     [:h2 {:class "text-sm font-medium text-base-content/60 mb-2 uppercase tracking-wide"}
      (i18n/t :feed/today)]
     (if (seq today-entries)
       (let [latest (first today-entries)
             rest-entries (rest today-entries)
             soft-order? (list (insights/feed-widget state-label insight ai-advice)
                               (hero-card latest chart))
             normal-order? (list (hero-card latest chart)
                                 (insights/feed-widget state-label insight ai-advice))]
         [:div
          (if soft?
            soft-order?
            normal-order?)
          (when (seq rest-entries)
            [:div {:class "space-y-2 mt-2"}
             (map #(entry-card csrf %) rest-entries)])])
       (empty-state))]))

(defn- timeline-day
  "Один день timeline (макет lenta 131–161): дата-заголовок, точка на
   вертикальной линии — цвет по state последней записи дня (Decision 1),
   карточки ml-8. Контейнер relative: точка привязана к нему, линия —
   к общему контейнеру в past-day-section. id #feed-day-<date> — цель
   OOB-удаления пустого дня (design D4)."
  [csrf date entries]
  [:div {:id (str "feed-day-" date) :class "relative mt-3"}
   [:div {:class "text-sm text-base-content/60 mb-2 pl-8"} (day-header date)]
   [:span {:class (str "absolute left-[1px] top-[34px] w-3.5 h-3.5 "
                       "rounded-full border-2 border-base-100 "
                       (day-dot-class (first entries)))}]
   [:div {:class "ml-8 space-y-2"}
    (map #(entry-card csrf %) entries)]])

(defn- older-sentinel
  "Самозаменяющий sentinel подгрузки старых дней (design D6): при появлении
   в зоне видимости htmx шлёт GET /feed/older?before=<дата> и заменяет себя
   фрагментом следующих дней + свежим sentinel."
  [before]
  [:div {:id "feed-older"
         :hx-get (str "/feed/older?before=" before)
         :hx-trigger "revealed"
         :hx-swap "outerHTML"}])

(defn older-fragment
  "Фрагмент подгрузки старых дней ленты (design D6): timeline-секции дней
   чанка + свежий sentinel, либо nil при исчерпании. Переиспользует
   timeline-day/entry-card."
  [csrf {:keys [chunks next-before]}]
  (when (seq chunks)
    (concat
     (map (fn [{:keys [date entries]}] (timeline-day csrf date entries)) chunks)
     (when next-before [(older-sentinel next-before)]))))

(defn- past-day-section
  "Timeline прошедших дней (макет lenta 127–178): одна вертикальная линия
   на весь контейнер + секция timeline-day на каждый день + sentinel
   подгрузки старых дней (design D6). past-dates: даты (date desc);
   grouped: map дата → записи дня; next-before: дата для /feed/older или nil."
  [csrf past-dates grouped next-before]
  (when (or (seq past-dates) next-before)
    [:div {:class "relative"}
     [:div {:class "absolute left-[7px] top-2 bottom-0 w-0.5 bg-base-300"}]
     (for [date past-dates]
       (timeline-day csrf date (get grouped date)))
     (when next-before
       (older-sentinel next-before))]))

(defn- period-banner
  "Баннер периода: активный — индикатор + «закрыть», иначе — «начать период».
   period: map {:active :list :csrf}."
  [period]
  (let [csrf (:csrf period)
        active (:active period)]
    (if active
      (sp/active-indicator csrf active)
      (sp/start-banner csrf))))

(defn page
  "Отрендерить страницу /feed: лента записей, сгруппированных по дням.
   Последняя запись сегодня — hero-карточка с линейным графиком периода
   (неделя по умолчанию); прошедшие дни — timeline-секции с точками
   состояния и чипами метрик.
   Под hero — виджет инсайтов (state-label, insight).
   request: ring-запрос; entries: вектор записей (date desc, created_at desc).
   opts: map с ключами :toast-insight (toast после сохранения),
   :summary (map {:show :csrf :state-label :insight} для вечерней сводки),
   :period (map {:active :list :csrf} для секции периода),
   :chart (фрагмент feed/chart-fragment для hero) и
   :ai (map {:csrf :correlations :label :advice :novel} для секций AI)."
  [request entries state-label insight & [{:keys [toast-insight summary period ai csrf episode chart]}]]
  (let [grouped (group-by :date entries)
        today (str (java.time.LocalDate/now))
        today-entries (get grouped today)
        past-dates (remove #{today} (keys grouped))
        ;; Sentinel подгрузки: самая старая дата окна или сегодня, если окно
        ;; содержит только сегодня (design D6)
        next-before (when (seq entries)
                      (or (last past-dates) today))
        content [:div {}
                 (when toast-insight
                   (notifications/hint-toast toast-insight))
                 (when (:show summary)
                   (notifications/summary-banner (:csrf summary)
                                                 (:state-label summary)
                                                 (:insight summary)))
                 (notifications/pending-insight-fragment)
                 (when episode
                   (ai/episode-warning-fragment csrf episode))
                 [:div {:class "mb-6 flex items-center justify-between"}
                  [:h1 {:class "text-2xl font-bold"} (i18n/t :feed/title)]
                  [:a {:href "/entries"
                       :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                       :aria-label (i18n/t :entries/list)}
                   (icons/svg "list-bullet" {:class "text-base-content/70"})]]

                 (when period
                   (period-banner period))
                 (when ai
                   (today-section today-entries state-label insight chart csrf
                                  {:csrf (:csrf ai)
                                   :findings (:advice ai)}))
                 (when ai
                   (week-chart-section entries))
                 (when (and ai (seq (:label ai)))
                   (ai/ai-state-label (:csrf ai) (:label ai)))
                 (when (and ai (seq (:correlations ai)))
                   (ai/ai-correlations (:csrf ai) (:correlations ai)))
                 (when (and ai (seq (:novel ai)))
                   (ai/ai-novel-advice (:csrf ai) (:novel ai)))
                 (when-not ai
                   (today-section today-entries state-label insight chart csrf))
                 (when period
                   (sp/period-list (:list period)))
                 (past-day-section csrf past-dates grouped next-before)]]
    (layout/layout {:title (i18n/t :feed/title)
                    :active :feed
                    :request request}
                   navigation/nav-items
                   content)))