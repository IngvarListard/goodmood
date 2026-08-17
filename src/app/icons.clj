(ns app.icons
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :refer [html raw]]))

(def ^:private valid-variants
  #{:outline :solid})

(def ^:private variant->dir
  {:outline "outline"
   :solid "solid"})

(def ^:private icon-size
  [24 24])

(defn- icon-path
  [name variant]
  (str "icons/heroicons/" (variant->dir variant) "/" name ".svg"))

(defn- inject-size
  [svg-string]
  (let [[w h] icon-size]
    (str/replace-first svg-string "<svg " (str "<svg width=\"" w "\" height=\"" h "\" "))))

(defn- load-svg
  [name variant]
  (let [path (icon-path name variant)
        resource (io/resource path)]
    (when-not resource
      (throw (ex-info (str "Icon not found: " name " (variant: " variant ")")
                      {:name name :variant variant :path path})))
    (inject-size (slurp resource))))

(def ^:private memoized-load-svg
  (memoize load-svg))

(defn svg
  "Загрузить SVG-иконку из heroicons и вернуть hiccup-элемент.
   Иконка обёрнута в блок фиксированного размера (w-6 h-6) с явным
   размером svg (24×24), чтобы иконки всегда рендерились одинаково
   независимо от контейнера.
   Опции:
     :variant — :outline (по умолчанию) или :solid
     :class   — CSS-класс, добавляется к обёрточному span"
  ([name] (svg name {}))
  ([name {:keys [variant class] :or {variant :outline}}]
   (when-not (contains? valid-variants variant)
     (throw (ex-info (str "Unknown variant: " variant ". Use :outline or :solid.")
                     {:variant variant :valid-variants valid-variants})))
   [:span {:class (str "inline-flex items-center justify-center w-6 h-6 shrink-0 "
                       (or class ""))}
    (raw (memoized-load-svg name variant))]))