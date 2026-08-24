(ns app.db.state-periods
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- now []
  (str (java.time.LocalDateTime/now)))

(defn create-period!
  "Создать период состояния. started-at — строка ISO или nil (по умолчанию
   текущее время). Возвращает созданную строку."
  [ds {:keys [user-id label started-at ended-at notes]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :state_periods
                :values [{:user_id user-id
                          :label label
                          :started_at (or started-at (now))
                          :ended_at ended-at
                          :notes notes}]
                :returning [:*]})
   default-opts))

(defn get-active-period
  "Активный (незакрытый) период пользователя — самый свежий по started_at,
   или nil. Несколько открытых периодов не ожидаются (UI закрывает перед
   новым), берём самый последний."
  [ds user-id]
  (first (jdbc/execute!
          ds
          (sql/format {:select [:*]
                       :from [:state_periods]
                       :where [:and [:= :user_id user-id]
                               [:is :ended_at nil]]
                       :order-by [[:started_at :desc]]
                       :limit 1})
          default-opts)))

(defn get-active-period-id
  "id активного периода пользователя или nil."
  [ds user-id]
  (:id (get-active-period ds user-id)))

(defn get-period
  "Период по id+user_id или nil (чужой/несуществующий)."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from [:state_periods]
                :where [:and [:= :id id] [:= :user_id user-id]]})
   default-opts))

(defn get-periods
  "Список периодов пользователя, свежайшие первые (для ретроспективы)."
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from [:state_periods]
                :where [:= :user_id user-id]
                :order-by [[:started_at :desc]]})
   default-opts))

(defn close-period!
  "Закрыть период: заполнить ended_at. Ограничено user-id; закрывает только
   открытый период. Возвращает обновлённую строку или nil."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:update :state_periods
                :set {:ended_at (now)}
                :where [:and [:= :id id]
                        [:= :user_id user-id]
                        [:is :ended_at nil]]
                :returning [:*]})
   default-opts))