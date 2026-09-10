(ns app.icons
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :refer [html raw]]))

(def ^:private valid-variants
  #{:outline :solid})

(def ^:private variant->dir
  {:outline "outline"
   :solid "solid"})

(defn- icon-path
  [name variant]
  (str "icons/heroicons/" (variant->dir variant) "/" name ".svg"))

(defn- inject-size
  "Вставить явные размеры 24×24 в svg-строку."
  [svg-string]
  (str/replace-first svg-string "<svg " "<svg width=\"24\" height=\"24\" "))

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
   независимо от контейнера. Неизвестная variant → ex-info из load-svg
   (иконка не найдена).
   Опции:
     :variant — :outline (по умолчанию) или :solid
     :class   — CSS-класс, добавляется к обёрточному span"
  ([name] (svg name {}))
  ([name {:keys [variant class] :or {variant :outline}}]
   [:span {:class (str "inline-flex items-center justify-center w-6 h-6 shrink-0 "
                       (or class ""))}
    (raw (memoized-load-svg name variant))]))