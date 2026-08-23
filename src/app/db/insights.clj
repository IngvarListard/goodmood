(ns app.db.insights
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- parse-advice
  "Распарсить JSON-строку advice_to_self в вектор строк, пустой вектор при ошибке."
  [s]
  (try
    (vec (json/read-str (or s "[]")))
    (catch Exception _ [])))

(defn- serialize-advice
  "Сериализовать вектор строк в JSON для хранения в БД."
  [advice]
  (json/write-str (vec advice)))

(defn- decorate-row
  "Преобразовать строку из БД: распарсить advice_to_self из JSON в вектор."
  [row]
  (when row
    (assoc row :advice-to-self (parse-advice (:advice-to-self row)))))

(defn create-insight!
  "Создать инсайт-артефакт. advice — вектор строк, сериализуется в JSON."
  [ds {:keys [user-id context category advice-to-self identity state-label entry-id]}]
  (-> (jdbc/execute-one!
       ds
       (sql/format {:insert-into :insights
                    :values [{:user_id user-id
                              :context context
                              :category category
                              :advice_to_self (serialize-advice advice-to-self)
                              :identity identity
                              :state_label state-label
                              :entry_id entry-id}]
                    :returning [:*]})
       default-opts)
      decorate-row))

(defn get-insight
  "Достать инсайт пользователя по id (выборка ограничена user-id)."
  [ds user-id id]
  (-> (jdbc/execute-one!
       ds
       (sql/format {:select [:*]
                    :from [:insights]
                    :where [:and [:= :id id] [:= :user_id user-id]]})
       default-opts)
      decorate-row))

(defn get-insights
  "Список всех инсайтов пользователя, отсортированных по updated_at DESC.
   Опциональный category-фильтр."
  ([ds user-id]
   (->> (jdbc/execute!
         ds
         (sql/format {:select [:*]
                      :from [:insights]
                      :where [:= :user_id user-id]
                      :order-by [[:updated_at :desc]]})
         default-opts)
        (map decorate-row)))
  ([ds user-id category]
   (if (str/blank? category)
     (get-insights ds user-id)
     (->> (jdbc/execute!
           ds
           (sql/format {:select [:*]
                        :from [:insights]
                        :where [:and [:= :user_id user-id] [:= :category category]]
                        :order-by [[:updated_at :desc]]})
           default-opts)
          (map decorate-row)))))

(defn get-matching-insights
  "Релевантные инсайты по state_label (exact match), отсортированные по
   updated_at DESC. limit — максимум N инсайтов (для /feed = 1)."
  [ds user-id state-label limit]
  (->> (jdbc/execute!
        ds
        (sql/format {:select [:*]
                     :from [:insights]
                     :where [:and [:= :user_id user-id] [:= :state_label state-label]]
                     :order-by [[:updated_at :desc]]
                     :limit limit})
        default-opts)
       (map decorate-row)))

(defn- now []
  (str (java.time.LocalDateTime/now)))

(defn update-insight-field!
  "Обновить одно поле инсайта (context/advice_to_self/identity/category) и updated_at.
   advice — вектор строк (сериализуется в JSON); остальные поля — строки.
   Возвращает обновлённую строку или nil."
  [ds user-id id field value]
  (let [db-value (if (= field :advice_to_self)
                   (serialize-advice value)
                   value)]
    (-> (jdbc/execute-one!
         ds
         (sql/format {:update :insights
                      :set {field db-value
                            :updated_at (now)}
                      :where [:and [:= :id id] [:= :user_id user-id]]
                      :returning [:*]})
         default-opts)
        decorate-row)))

(defn delete-insight!
  "Удалить инсайт (hard delete) по id+user_id."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:delete-from :insights
                :where [:and [:= :id id] [:= :user_id user-id]]})
   default-opts))
