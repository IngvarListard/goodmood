(ns app.views.rose
  (:require [app.i18n :as i18n]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private size 200)

(def ^:private axis-ks
  "Порядок осей радара: energy сверху, далее по часовой."
  [:energy :anxiety :focus :mood])

(defn- axis-labels
  "i18n-метки первых n осей (:entries/energy и т.п.)."
  [n]
  (mapv #(i18n/t (keyword "entries" (name %))) (take n axis-ks)))

(defn- axis-values
  "Значения первых n осей по карте axes (mood берётся из :mood-score),
   nil → 0 (как в прежней SVG-версии)."
  [axes n]
  (let [m {:energy (:energy axes)
           :anxiety (:anxiety axes)
           :focus (:focus axes)
           :mood (:mood-score axes)}]
    (mapv #(or (get m %) 0) (take n axis-ks))))

(defn- radar-canvas
  "canvas 200×200: данные (JSON {labels values} в data-gm-radar) и
   aria-label — сервер, пиксели — resources/public/js/radar.js."
  [labels values aria-label]
  [:canvas {:class "gm-radar"
            :width size :height size
            :role "img"
            :aria-label aria-label
            :data-gm-radar (json/generate-string {:labels labels
                                                  :values values})}])

(defn radar
  "Отрендерить canvas радара «роза ветров» для записи, который рисует на
   клиенте resources/public/js/radar.js (Chart.js, стиль gradient+glow).
   opts: {:keys [energy anxiety focus mood-score]} — оси 0-10, nil допустим
   (трактуется как 0).
   3 оси (energy/anxiety/focus) или 4 (с mood-score); порядок осей задаёт
   раскладку: energy сверху, далее по часовой.
   Данные и тексты — здесь (i18n-метки, aria-label, JSON в data-gm-radar),
   пиксели — в JS."
  [{:keys [energy anxiety focus mood-score] :as entry}]
  (let [n (if (some? mood-score) 4 3)]
    (radar-canvas (axis-labels n) (axis-values entry n)
                  (str "Роза ветров: энергия " (or energy "-")
                       ", тревога " (or anxiety "-")
                       ", фокус " (or focus "-")))))

(defn- period-btn
  "Кнопка-переключатель периода радара: hx-get /feed/radar?period=…
   свапает контейнер #radar-period (outerHTML); активная подсвечена и
   помечена aria-pressed."
  [label-kw current p]
  [:button {:class (if (= current p)
                     "btn btn-xs btn-primary"
                     "btn btn-xs btn-ghost")
            ;; строкой: hiccup рендерит boolean true как голый атрибут
            :aria-pressed (if (= current p) "true" "false")
            :hx-get (str "/feed/radar?period=" (name p))
            :hx-target "#radar-period"
            :hx-swap "outerHTML"}
   (i18n/t label-kw)])

(defn period-radar
  "Фрагмент «радар периода» hero-карточки /feed: canvas с агрегатом осей
   за период, подпись периода и «среднее с учётом свежести», кнопки
   день/неделя/месяц. axes — агрегат app.domains.entries/period-axes
   (:energy :anxiety :focus :mood-score, mood_score в БД NOT NULL — всегда
   4 оси); period — :day/:week/:month (подпись и активная кнопка);
   empty? — в периоде нет записей (aria-label «нет данных»)."
  [axes period & [empty?]]
  (let [labels (axis-labels 4)
        values (axis-values axes 4)
        aria (if empty?
               (i18n/t :feed/radar-no-data)
               (str "Роза ветров: "
                    (str/join ", " (map #(str %1 " " %2) labels values))))]
    [:div {:id "radar-period" :class "shrink-0 mx-auto"}
     (radar-canvas labels values aria)
     [:p {:class "text-[13px] text-base-content/60 mt-1"}
      (i18n/t (keyword "feed" (str "period-" (name period))))]
     [:p {:class "text-[11px] text-base-content/40"}
      (i18n/t :feed/radar-weighted)]
     [:div {:class "flex justify-center gap-1 mt-1"}
      (period-btn :feed/period-day period :day)
      (period-btn :feed/period-week period :week)
      (period-btn :feed/period-month period :month)]]))
