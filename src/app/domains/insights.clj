(ns app.domains.insights
  (:require [app.db.insights :as db]
            [cheshire.core :as json]
            [clojure.string :as str]
            [malli.core :as mc]
            [malli.error :as me]))

(def categories
  "Допустимые значения category (см. миграция 007 CHECK)."
  #{"productivity" "coping" "identity" "general"})

(def state-labels
  "Допустимые значения state_label (6 ярлыков розы ветров, см. domains.entries/state-label)."
  #{"mixed" "anxiety" "elevated" "low" "balanced" "neutral"})

(def create-insight-schema
  "Malli-схема создания инсайта.
   context — non-empty строка.
   category — enum из 4 значений.
   advice_to_self — строка (один совет из формы) или вектор строк.
   identity — опциональная nullable строка.
   state_label — nullable enum из 6 ярлыков.
   entry_id — nullable int (soft reference на entries.id)."
  [:map
   [:context [:string {:min 1}]]
   [:category [:enum "productivity" "coping" "identity" "general"]]
   [:advice_to_self {:optional true} [:or :string [:vector :string]]]
   [:identity {:optional true} [:maybe :string]]
   [:state_label {:optional true} [:maybe [:enum "mixed" "anxiety" "elevated" "low" "balanced" "neutral"]]]
   [:entry_id {:optional true} [:maybe :int]]])

(defn- normalize-advice
  "Привести input к вектору строк и отфильтровать пустые.
   Принимает строку (один совет из одного textarea), JSON-массив-строку
   (сериализация нескольких textarea через hyperscript) или вектор."
  [advice]
  (when advice
    (let [raw (if (and (string? advice)
                       (str/starts-with? (str/trim advice) "["))
                (try (vec (json/parse-string advice))
                     (catch Exception _ [advice]))
                (if (string? advice) [advice] advice))]
      (->> raw
           (map str/trim)
           (remove str/blank?)
           vec))))

(defn- blank->nil
  "Вернуть nil для пустой строки, иначе исходное значение."
  [v]
  (if (and (string? v) (str/blank? v))
    nil
    v))

(defn validate-create
  "Валидировать параметры создания инсайта через malli.
   Возвращает nil при успехе, или map ошибок (humanized) при неудаче."
  [params]
  (when-not (mc/validate create-insight-schema params)
    (-> create-insight-schema
        (mc/explain params)
        me/humanize)))

(defn- normalized-params
  "Нормализовать параметры создания: advice (строка/вектор) → вектор,
   identity (blank→nil), context trimmed."
  [{:keys [context category advice_to_self identity state_label entry_id]}]
  {:context (str/trim (or context ""))
   :category category
   :advice-to-self (normalize-advice advice_to_self)
   :identity (blank->nil identity)
   :state-label state_label
   :entry-id entry_id})

(defn create-insight
  "Создать инсайт: валидация (malli), нормализация advice (trim, filter empty),
   нормализация identity (blank→nil), сериализация advice в JSON на уровне БД.
   Принимает snake_case-ключи как в API-схеме.
   Возвращает созданный инсайт или бросает ExceptionInfo с :errors при невалидных данных."
  [ds user-id params]
  (let [errors (validate-create params)]
    (when errors
      (throw (ex-info "Insight validation failed" {:errors errors})))
    (let [n (normalized-params params)]
      (when-not (seq (:advice-to-self n))
        (throw (ex-info "Insight validation failed"
                        {:errors {:advice_to_self ["at least one advice item is required"]}})))
      (db/create-insight! ds {:user-id user-id
                              :context (:context n)
                              :category (:category n)
                              :advice-to-self (:advice-to-self n)
                              :identity (:identity n)
                              :state-label (:state-label n)
                              :entry-id (:entry-id n)}))))

(defn get-insight
  "Достать инсайт пользователя или nil."
  [ds user-id id]
  (db/get-insight ds user-id id))

(defn list-insights
  "Список всех инсайтов пользователя, опционально с category-фильтром."
  ([ds user-id]
   (db/get-insights ds user-id))
  ([ds user-id category]
   (if (str/blank? category)
     (db/get-insights ds user-id)
     (db/get-insights ds user-id category))))

(defn matching-insight
  "Найти 1 релевантный инсайт по state_label (exact match) для /feed.
   Возвращает инсайт или nil."
  [ds user-id state-label]
  (when (and state-label (seq state-label))
    (first (db/get-matching-insights ds user-id state-label 1))))

(defn- valid-field?
  "Проверить, что field — одно из редактируемых полей (context/advice_to_self/identity/category)."
  [field]
  (#{:context :advice_to_self :identity :category} field))

(defn update-field
  "Обновить одно поле инсайта (context/advice_to_self/identity/category).
   advice_to_self передаётся как вектор строк (сериализуется в БД).
   Возвращает обновлённый инсайт или nil (чужой user / несуществующий id).
   state_label НЕ редактируемый (Decision 13.8)."
  [ds user-id id field value]
  {:pre [(valid-field? field)]}
  (let [normalized-value
        (case field
          :context (str/trim value)
          :advice_to_self (normalize-advice value)
          :identity (blank->nil value)
          :category value
          value)]
    (db/update-insight-field! ds user-id id field normalized-value)))

(defn delete-insight
  "Удалить инсайт (hard delete) по id+user-id."
  [ds user-id id]
  (db/delete-insight! ds user-id id))
