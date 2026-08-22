(ns app.views.rose
  (:require [app.i18n :as i18n]
            [hiccup2.core :refer [html]]))

(def ^:private size 200)

(def ^:private cx 100)

(def ^:private cy 100)

(def ^:private max-radius 80)

(defn- axis-angle
  "Угол оси i из n (старт сверху, по часовой)."
  [i n]
  (+ (- (/ Math/PI 2)) (/ (* 2 Math/PI i) n)))

(defn- polar-point
  "Перевести полярные координаты (angle, value 0-10) в декартовы для SVG.
   nil value трактуется как 0 (ось не заполнена). Координаты округляются
   до 1 знака для стабильного вывода."
  [angle value]
  (let [v (or value 0)
        r (* max-radius (/ v 10))]
    {:x (/ (Math/round (* 10 (+ cx (* r (Math/cos angle))))) 10.0)
     :y (/ (Math/round (* 10 (+ cy (* r (Math/sin angle))))) 10.0)}))

(defn- points->str
  "Преобразовать вектор точек в строку атрибута points SVG."
  [points]
  (apply str (interpose " " (map (fn [p] (str (:x p) "," (:y p))) points))))

(defn- axis-specs
  "Собрать спецификации осей из значений. Возвращает векторы [label value angle label-x label-y]."
  [energy anxiety focus mood-score]
  (let [n (if (some? mood-score) 4 3)]
    (cond-> [{:label (i18n/t :entries/energy) :value energy
              :angle (axis-angle 0 n) :label-x 100 :label-y 12}
             {:label (i18n/t :entries/anxiety) :value anxiety
              :angle (axis-angle 1 n) :label-x 178 :label-y 148}
             {:label (i18n/t :entries/focus) :value focus
              :angle (axis-angle 2 n) :label-x 22 :label-y 148}]
      mood-score (conj {:label (i18n/t :entries/mood) :value mood-score
                        :angle (axis-angle 3 n) :label-x 100 :label-y 190}))))

(defn radar
  "Отрендерить read-only SVG-радар «розу ветров» (200×200).
   opts: {:keys [energy anxiety focus mood-score]} — оси 0-10.
   3 оси (energy/anxiety/focus) или 4 (с mood-score).
   Hand-rolled SVG, без JS-библиотек."
  [{:keys [energy anxiety focus mood-score] :as opts}]
  (let [axes (axis-specs energy anxiety focus mood-score)
        outer-points (map (fn [{:keys [angle]}] (polar-point angle 10)) axes)
        value-points (map (fn [{:keys [angle value]}] (polar-point angle value)) axes)
        grid-scales [0.333 0.667 1.0]
        grid-opacity [0.08 0.12 0.2]]
    [:svg {:width size :height size :viewBox (str "0 0 " size " " size)
           :xmlns "http://www.w3.org/2000/svg"
           :class "text-base-content"
           :role "img"
           :aria-label (str "Роза ветров: энергия " (or energy "-")
                            ", тревога " (or anxiety "-")
                            ", фокус " (or focus "-"))}
     ;; grid-кольца (3 концентрических треугольника)
     (map (fn [scale opacity]
            (let [pts (map (fn [{:keys [angle]}] (polar-point angle (* 10 scale))) axes)]
              [:polygon {:points (points->str pts)
                         :fill "none" :stroke "currentColor"
                         :stroke-opacity opacity :stroke-width 1}]))
          grid-scales grid-opacity)
     ;; оси-линии (center → outer вершины)
     (map (fn [p]
            [:line {:x1 cx :y1 cy :x2 (:x p) :y2 (:y p)
                    :stroke "currentColor" :stroke-opacity 0.2
                    :stroke-width 1}])
          outer-points)
     ;; value-полигон
     [:polygon {:points (points->str value-points)
                :fill "hsl(var(--p))" :fill-opacity 0.3
                :stroke "hsl(var(--p))" :stroke-width 2
                :stroke-linejoin "round"}]
     ;; вершины полигона (точки)
     (map (fn [p]
            [:circle {:cx (:x p) :cy (:y p) :r 3 :fill "hsl(var(--p))"}])
          value-points)
     ;; метки осей
     (map (fn [{:keys [label label-x label-y]}]
            [:text {:x label-x :y label-y :text-anchor "middle"
                    :class "text-[11px] fill-base-content"
                    :fill-opacity 0.6}
             label])
          axes)
     ;; значения рядом с точками полигона (пропускаются если ось не заполнена)
     (keep (fn [[p v]]
             (when v
               [:text {:x (:x p) :y (- (:y p) 9) :text-anchor "middle"
                       :class "text-[11px] font-semibold fill-base-content"}
                (str v)]))
           (map vector value-points (map :value axes)))
     ;; center dot
     [:circle {:cx cx :cy cy :r 2 :fill "currentColor" :fill-opacity 0.5}]]))