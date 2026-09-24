(ns app.db.entries
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn create-entry!
  [ds {:keys [user-id date activity effect mood-score energy anxiety focus
              aggression sleep-hours note template state-label state-period-id
              created-at]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :entries
                :values [(cond-> {:user_id user-id
                                  :date date
                                  :activity activity
                                  :effect effect
                                  :mood_score mood-score
                                  :energy energy
                                  :anxiety anxiety
                                  :focus focus
                                  :aggression aggression
                                  :sleep_hours sleep-hours
                                  :note note
                                  :template template
                                  :state_label state-label
                                  :state_period_id state-period-id}
                           created-at (assoc :created_at created-at))]
                :returning [:*]})
   default-opts))

(defn get-entries
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:= :user_id user-id]
                :order-by [[:date :desc] [:created_at :desc]]})
   default-opts))

(def older-row-limit
  "Row-limit выборки старого чанка (design D4): 5 дней с большим запасом.
   ponytail: row-limit на чанк; при дне >100 строк граничный день может
   обрезаться — перейти на DISTINCT-date запрос."
  500)

(defn get-entries-since
  "Записи пользователя с датой >= since (ISO), свежие первыми. Стартовое
   окно страниц (design D2); график использует ту же выборку (group-by :date)."
  [ds user-id since]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:and [:= :user_id user-id] [:>= :date since]]
                :order-by [[:date :desc] [:created_at :desc]]})
   default-opts))

(defn get-entries-before
  "Записи пользователя строго старше before (ISO), свежие первыми, не больше
   older-row-limit строк (design D2/D4)."
  [ds user-id before]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:and [:= :user_id user-id] [:< :date before]]
                :order-by [[:date :desc] [:created_at :desc]]
                :limit older-row-limit})
   default-opts))

(defn get-entries-on-date
  "Записи пользователя за конкретную дату — подсчёт остатка дня при удалении
   с ленты после пагинации (окно страницы может не содержать эту дату)."
  [ds user-id date]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:and [:= :user_id user-id] [:= :date date]]
                :order-by [[:created_at :desc]]})
   default-opts))

(defn count-entries
  "Общее число записей пользователя (для счётчика /entries, design D8)."
  [ds user-id]
  (or (:n (jdbc/execute-one!
           ds
           (sql/format {:select [[:%count.* :n]]
                        :from [:entries]
                        :where [:= :user_id user-id]})
           default-opts))
      0))

(defn get-latest-entry
  "Последняя запись пользователя (date desc, created_at desc) или nil
   (design D9: «последняя запись» независима от окна пагинации)."
  [ds user-id]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:= :user_id user-id]
                :order-by [[:date :desc] [:created_at :desc]]
                :limit 1})
   default-opts))

(defn get-entry
  "Одна запись пользователя по id (выборка ограничена user-id), или nil."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from [:entries]
                :where [:and [:= :id id] [:= :user_id user-id]]})
   default-opts))

(defn set-state-label!
  "Установить ручной state_label записи (ограничено user-id).
   Возвращает обновлённую запись или nil (чужая запись / несуществующий id)."
  [ds user-id id label]
  (jdbc/execute-one!
   ds
   (sql/format {:update :entries
                :set {:state_label label}
                :where [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))

(defn update-entry!
  "Обновить переданные поля записи (SET только ключей из fields, snake-case),
   WHERE id AND user_id. Возвращает обновлённую запись или nil (чужая/нет id)."
  [ds user-id id fields]
  (when (seq fields)
    (jdbc/execute-one!
     ds
     (sql/format {:update :entries
                  :set fields
                  :where [:and [:= :id id] [:= :user_id user-id]]
                  :returning [:*]})
     default-opts)))

(defn delete-entry!
  "Удалить запись (hard delete) по id+user_id.
   Возвращает удалённую строку или nil (чужая запись / несуществующий id)."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:delete-from :entries
                :where [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))