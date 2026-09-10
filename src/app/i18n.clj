(ns app.i18n
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [tongue.core :as tongue]))

(def supported-locales [:ru :en])

(def default-locale :ru)

(def ^:dynamic *locale*
  "Текущая локаль запроса, устанавливается middleware wrap-locale."
  default-locale)

(defn normalize-locale
  "Нормализовать строку локали (напр. \"en-US\") до поддерживаемого keyword, или nil."
  [s]
  (when (and (string? s) (seq s))
    (let [lang (-> s (str/split #"[-_]") first str/lower-case)]
      (when (seq lang)
        (keyword lang)))))

(defn supported-locale
  "Вернуть keyword локали если она поддерживается, иначе nil."
  [locale]
  (when (contains? (set supported-locales) locale)
    locale))

(defn ru-plural-form
  "Селектор русских множественных форм: :one (1, 21, 31…), :few (2–4, 22–24…), :many — остальные."
  [n]
  (let [n (Math/abs (long n))
        d (mod n 10)
        dd (mod n 100)]
    (cond
      (and (= d 1) (not= dd 11)) :one
      (and (>= d 2) (<= d 4)
           (not (and (>= dd 12) (<= dd 14)))) :few
      :else :many)))

(defn en-plural-form
  "Селектор английских множественных форм: :one для 1, :many для остальных."
  [n]
  (if (= 1 (Math/abs (long n))) :one :many))

(defn- plural-selector
  [locale]
  (case locale
    :ru ru-plural-form
    :en en-plural-form
    (fn [_] :many)))

(defn- plural-template
  "Построить шаблон функции tongue для ключей с -plural.
   Значение {:one … :few … :many …} разрешается селектором множественных
   чисел локали; количество берётся из ключа интерполяции :n."
  [locale forms]
  (fn [x]
    (let [n (if (map? x) (:n x) x)
          form ((plural-selector locale) n)]
      (str n " " (get forms form (get forms :many))))))

(defn- compile-plurals
  "Рекурсивно заменить map множественных чисел ({:one :few :many}) на
   шаблоны функций множественных чисел."
  [dict locale]
  (reduce-kv
   (fn [acc k v]
     (cond
       (and (map? v)
            (or (contains? v :one) (contains? v :few) (contains? v :many)))
       (assoc acc k (plural-template locale v))

       (map? v)
       (assoc acc k (compile-plurals v locale))

       :else
       (assoc acc k v)))
   {} dict))

(defn- load-dict
  [locale]
  (edn/read-string (slurp (io/resource (str "i18n/" (name locale) ".edn")))))

(def ^:private dicts
  (->> supported-locales
       (map (fn [locale]
              [locale (-> (load-dict locale)
                          (compile-plurals locale))]))
       (into {:tongue/fallback default-locale})))

(def ^:private translate
  (tongue/build-translate dicts))

(defn t
  "Перевести ключ для текущей *locale*. Поддерживает именованные ({name}) и
   позиционные ({1}) плейсхолдеры, а также plural-ключи c {:n count}."
  ([key]
   (translate *locale* key))
  ([key x]
   (translate *locale* key x))
  ([key x & more]
   (apply translate *locale* key x more)))

