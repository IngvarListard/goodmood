(ns app.i18n
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [tongue.core :as tongue]))

(def supported-locales [:ru :en])

(def default-locale :ru)

(def ^:dynamic *locale*
  "Current request locale, bound by wrap-locale middleware."
  default-locale)

(defn normalize-locale
  "Normalize a locale string (e.g. \"en-US\") to a supported keyword, or nil."
  [s]
  (when (and (string? s) (seq s))
    (let [lang (-> s (str/split #"[-_]") first str/lower-case)]
      (when (seq lang)
        (keyword lang)))))

(defn supported-locale
  "Return locale keyword if supported, else nil."
  [locale]
  (when (contains? (set supported-locales) locale)
    locale))

(defn ru-plural-form
  "Russian plural form selector: :one (1, 21, 31...), :few (2-4, 22-24...), :many else."
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
  "English plural form selector: :one for 1, :many else."
  [n]
  (if (= 1 (Math/abs (long n))) :one :many))

(defn- plural-selector
  [locale]
  (case locale
    :ru ru-plural-form
    :en en-plural-form
    (fn [_] :many)))

(defn- plural-template
  "Build a tongue function template for -plural keys.
   The value map {:one .. :few .. :many ..} is resolved by the locale's
   plural selector; the count is taken from the :n interpolation key."
  [locale forms]
  (fn [x]
    (let [n (if (map? x) (:n x) x)
          form ((plural-selector locale) n)]
      (str n " " (get forms form (get forms :many))))))

(defn- compile-plurals
  "Recursively replace plural map values ({:one :few :many}) with
   plural function templates."
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
  "Translate key for the current *locale*. Supports named ({name}) and
   positional ({1}) placeholders, and plural keys taking {:n count}."
  ([key]
   (translate *locale* key))
  ([key x]
   (translate *locale* key x))
  ([key x & more]
   (apply translate *locale* key x more)))

(defn t-for
  "Translate key for an explicit locale."
  [locale key & args]
  (apply translate locale key args))