;; Одноразовая генерация PWA-иконок (change add-pwa-push, design D2):
;; белый глиф «розы ветров» на #5b5bea, maskable — глиф в safe-zone (80%).
;; Запуск: clj -M dev/gen_icons.clj  (артефакты коммитятся в resources/public/icons)
(require '[clojure.java.io :as io])
(import '[java.awt RenderingHints Color]
        '[java.awt.geom Path2D$Double]
        '[java.awt.image BufferedImage]
        '[javax.imageio ImageIO])

(def ^:private bg 0xff5b5bea)     ; --color-primary, ARGB с полной альфой
(def ^:private glyph 0xffffffff)

(defn- rose
  "8-конечная роза ветров: восемь ромбовидных лучей вокруг центра (cx cy).
   r — радиус длинных (север/восток/юг/запад) лучей, диагональные — 0.55r;
   основание луча — точки на ±22.5° от его оси на радиусе 0.25r."
  [^double cx ^double cy ^double r]
  (let [path (Path2D$Double.)
        half (* 0.125 Math/PI)               ; половина шага луча (22.5°)
        pt   (fn [angle len]
               (.lineTo path (+ cx (* len (Math/sin angle)))
                         (- cy (* len (Math/cos angle)))))]
    (dotimes [i 8]
      (let [a (* i Math/PI 0.25)
            len (if (odd? i) (* 0.55 r) r)]
        (.moveTo path cx cy)
        (pt (- a half) (* 0.25 r))
        (pt a len)
        (pt (+ a half) (* 0.25 r))
        (.closePath path)))
    path))

(defn- draw
  "Отрисовать иконку size×size. maskable? — глиф ужат в центр-круг 80%."
  [^long size maskable?]
  (let [img (BufferedImage. size size BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics img)
        cx (/ size 2.0)
        ;; Длинный луч: у maskable глиф целиком в safe-zone (r = 0.4*size),
        ;; у обычной — с полями (~0.31*size, как рекомендация для legacy)
        r (if maskable? (* 0.40 size) (* 0.31 size))]
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_ON)
    (.setColor g (Color. (unchecked-int bg))) ; фон — весь квадрат (maskable ok)
    (.fillRect g 0 0 size size)
    (.setColor g (Color. (unchecked-int glyph)))
    (.fill g (rose cx cx r))
    (.dispose g)
    img))

(doseq [{:keys [name size maskable?]}
        [{:name "icon-192.png" :size 192 :maskable? false}
         {:name "icon-512.png" :size 512 :maskable? false}
         {:name "icon-maskable-192.png" :size 192 :maskable? true}
         {:name "icon-maskable-512.png" :size 512 :maskable? true}]
        :let [out (io/file "resources/public/icons" name)]]
  (.mkdirs (.getParentFile out))
  (ImageIO/write (draw size maskable?) "png" out)
  (println "written" (str out)))