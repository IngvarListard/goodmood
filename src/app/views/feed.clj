(ns app.views.feed
  (:require [app.domains.entries :as domains]
            [app.i18n :as i18n]
            [app.icons :as icons]
            [app.views.ai :as ai]
            [app.views.insights :as insights]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]
            [app.views.notifications :as notifications]
            [app.views.rose :as rose]
            [app.views.state-periods :as sp]
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
  [:div {:class "gm-chip p-2.5 flex items-center gap-2"}
   [:span {:style {:color (str "var(" var-name ")")}}
    (icons/svg icon)]
   [:div
    [:div {:class "text-[11px] text-base-content/60"} (i18n/t label-kw)]
    [:div {:class "text-[15px]"} (or value "–")]]])

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

(defn- mood-fill-class
  "Класс заливки точки по порогам задачи 5: ≤3 — fill-success,
   4–6 — fill-warning, ≥7 — fill-error."
  [v]
  (cond
    (<= v 3) "fill-success"
    (<= v 6) "fill-warning"
    :else "fill-error"))

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
                       :class "stroke-primary"
                       :fill "none" :stroke-width 2}]
           (map-indexed (fn [i v]
                          (if (some? v)
                            [:circle {:cx (chart-point-x i)
                                      :cy (mood-point-y v) :r 6
                                      :class (mood-fill-class v)}]
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

(defn- entry-card
  "Карточка записи в timeline (макет lenta 134–144): state-бейдж слева,
   время и иконка меню справа; ниже — grid-cols-3 чипов метрик
   (Decision 3), отсутствующее значение — «–»."
  [{:keys [created-at] :as entry}]
  [:div {:class "card bg-base-200 shadow-sm"}
   [:div {:class "card-body p-3"}
    [:div {:class "flex items-center justify-between"}
     [:span {:class (str (state-badge-class entry) " text-[13px]")}
      (entry-label entry)]
     [:div {:class "flex items-center gap-3 text-sm text-base-content/60"}
      [:span {:class "tabular-nums"} (format-time created-at)]
      (icons/svg "ellipsis-horizontal" {:class "text-base-content/40"})]]
    [:div {:class "grid grid-cols-3 gap-2 mt-3"}
     (metric-chip :entries/energy "bolt" "--gm-metric-energy" (:energy entry))
     (metric-chip :entries/anxiety "fire" "--gm-metric-anxiety"
                  (:anxiety entry))
     (metric-chip :entries/focus "eye" "--gm-metric-focus" (:focus entry))]]])

(defn- hero-card
  "Hero-карточка последней записи сегодня: SVG-радар 200×200 + метаданные."
  [{:keys [sleep-hours note created-at] :as entry}]
  [:article {:class "card bg-base-200 shadow-md"}
   [:div {:class "card-body p-4"}
    [:div {:class "flex flex-col sm:flex-row gap-4 items-start"}
     [:div {:class "shrink-0 mx-auto"}
      (rose/radar (select-keys entry [:energy :anxiety :focus :mood-score]))]
     [:div {:class "flex-1 min-w-0 w-full"}
      [:div {:class "flex items-center justify-between"}
       [:span {:class (str (state-badge-class entry) " text-[13px]")}
        (entry-label entry)]
       [:span {:class "text-sm text-base-content/60 tabular-nums"}
        (format-time created-at)]]
      [:p {:class "text-sm text-base-content/85"} (axes-line entry)]
      (when sleep-hours
        [:p {:class "text-sm text-base-content/60 mt-1"}
         (str (i18n/t :entries/sleep) " "
              (format "%.1f" (double sleep-hours)) " ч")])
      (when (seq note)
        [:p {:class "text-sm text-base-content/80 mt-2"} note])]]]])

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
   В мягком состоянии (low/mixed) виджет поднимается выше hero — акцент
   на совете, не на фиксации боли (Decision 14.4)."
  [today-entries state-label insight & [ai-advice]]
  (let [soft? (and state-label (contains? #{"low" "mixed"} state-label))]
    [:section {:class "mb-6"}
     [:h2 {:class "text-sm font-medium text-base-content/60 mb-2 uppercase tracking-wide"}
      (i18n/t :feed/today)]
     (if (seq today-entries)
       (let [latest (first today-entries)
             rest-entries (rest today-entries)
             soft-order? (list (insights/feed-widget state-label insight ai-advice)
                               (hero-card latest))
             normal-order? (list (hero-card latest)
                                 (insights/feed-widget state-label insight ai-advice))]
         [:div
          (if soft?
            soft-order?
            normal-order?)
          (when (seq rest-entries)
            [:div {:class "space-y-2 mt-2"}
             (map entry-card rest-entries)])])
       (empty-state))]))

(defn- timeline-day
  "Один день timeline (макет lenta 131–161): дата-заголовок, точка на
   вертикальной линии — цвет по state последней записи дня (Decision 1),
   карточки ml-8. Контейнер relative: точка привязана к нему, линия —
   к общему контейнеру в past-day-section."
  [date entries]
  [:div {:class "relative mt-3"}
   [:div {:class "text-sm text-base-content/60 mb-2 pl-8"} (day-header date)]
   [:span {:class (str "absolute left-[1px] top-[34px] w-3.5 h-3.5 "
                       "rounded-full border-2 border-base-100 "
                       (day-dot-class (first entries)))}]
   [:div {:class "ml-8 space-y-2"}
    (map entry-card entries)]])

(defn- past-day-section
  "Timeline прошедших дней (макет lenta 127–178): одна вертикальная линия
   на весь контейнер + секция timeline-day на каждый день.
   past-dates: последовательность дат; grouped: map дата → записи дня."
  [past-dates grouped]
  (when (seq past-dates)
    [:div {:class "relative"}
     [:div {:class "absolute left-[7px] top-2 bottom-0 w-0.5 bg-base-300"}]
     (for [date past-dates]
       (timeline-day date (get grouped date)))]))

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
   Последняя запись сегодня — hero-карточка с розой; прошедшие дни —
   timeline-секции с точками состояния и чипами метрик.
   Под hero — виджет инсайтов (state-label, insight).
   request: ring-запрос; entries: вектор записей (date desc, created_at desc).
   opts: map с ключами :toast-insight (toast после сохранения),
   :summary (map {:show :csrf :state-label :insight} для вечерней сводки),
   :period (map {:active :list :csrf} для секции периода) и
   :ai (map {:csrf :correlations :label :advice :novel} для секций AI)."
  [request entries state-label insight & [{:keys [toast-insight summary period ai csrf episode]}]]
  (let [grouped (group-by :date entries)
        today (str (java.time.LocalDate/now))
        today-entries (get grouped today)
        past-dates (remove #{today} (keys grouped))
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
                 [:div {:class "mb-6"}
                  [:h1 {:class "text-2xl font-bold"} (i18n/t :feed/title)]]

                 (when period
                   (period-banner period))
                 (when ai
                   (today-section today-entries state-label insight
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
                   (today-section today-entries state-label insight))
                 (when period
                   (sp/period-list (:list period)))
                 (past-day-section past-dates grouped)]]
    (layout/layout {:title (i18n/t :feed/title)
                    :active :feed
                    :request request}
                   navigation/nav-items
                   content)))