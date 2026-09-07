(ns app.views.rose
  (:require [app.i18n :as i18n]
            [cheshire.core :as json]))

(def ^:private size 200)

(defn radar
  "Отрендерить контейнер радара «роза ветров»: canvas 200×200, который рисует
   на клиенте resources/public/js/radar.js (Chart.js, стиль gradient+glow).
   opts: {:keys [energy anxiety focus mood-score]} — оси 0-10, nil допустим
   (трактуется как 0, как в прежней SVG-версии).
   3 оси (energy/anxiety/focus) или 4 (с mood-score); порядок осей задаёт
   раскладку: energy сверху, далее по часовой.
   Данные и тексты — здесь (i18n-метки, aria-label, JSON в data-gm-radar),
   пиксели — в JS."
  [{:keys [energy anxiety focus mood-score]}]
  (let [axes [:energy :anxiety :focus :mood]
        n (if (some? mood-score) 4 3)
        labels (mapv #(i18n/t (keyword "entries" (name %))) (take n axes))
        values (mapv #(or (get {:energy energy
                                :anxiety anxiety
                                :focus focus
                                :mood mood-score} %) 0)
                     (take n axes))]
    [:canvas {:class "gm-radar"
              :width size :height size
              :role "img"
              :aria-label (str "Роза ветров: энергия " (or energy "-")
                               ", тревога " (or anxiety "-")
                               ", фокус " (or focus "-"))
              :data-gm-radar (json/generate-string {:labels labels
                                                    :values values})}]))