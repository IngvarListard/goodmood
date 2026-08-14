(ns app.db.users
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn create-user!
  [ds {:keys [email password-hash display-name role]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :users
                :values [{:email email
                          :password_hash password-hash
                          :display_name display-name
                          :role (or role "user")}]
                :returning [:*]})
   default-opts))

(defn get-user-by-email
  [ds email]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from [:users]
                :where [:= :email email]})
   default-opts))

(defn get-user-by-id
  [ds id]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from [:users]
                :where [:= :id id]})
   default-opts))

(defn count-users
  [ds]
  (:count
   (jdbc/execute-one!
    ds
    (sql/format {:select [[[:count :*] :count]]
                 :from [:users]})
    default-opts)))

(defn assign-orphan-entries!
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:update :entries
                :set {:user_id user-id}
                :where [:= :user_id nil]})))