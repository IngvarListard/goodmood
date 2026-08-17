(ns app.domains.users
  (:require [app.db.users :as db]
            [buddy.hashers :as hashers]
            [clojure.string :as str]))

(defn create-user
  "Создать пользователя с bcrypt-хешированным паролем.
   Возвращает map пользователя без :password-hash."
  [ds {:keys [email password display-name role]}]
  (let [user (db/create-user! ds {:email (str/trim email)
                                  :password-hash (hashers/derive password)
                                  :display-name display-name
                                  :role role})]
    (dissoc user :password-hash)))

(defn authenticate
  "Вернуть map пользователя (без :password-hash) если email и пароль совпадают,
   иначе nil."
  [ds email password]
  (when-let [user (db/get-user-by-email ds email)]
    (when (hashers/check password (:password-hash user))
      (dissoc user :password-hash))))

(defn register-user
  "Заглушка регистрации для будущего потока.
   Валидирует входные данные и создаёт пользователя с ролью по умолчанию.
   Возвращает map пользователя (без :password-hash)."
  [ds {:keys [email password display-name] :as user}]
  (when (str/blank? email)
    (throw (ex-info "Email is required" {:field :email})))
  (when (or (str/blank? password) (< (count password) 8))
    (throw (ex-info "Password must be at least 8 characters" {:field :password})))
  (when (str/blank? display-name)
    (throw (ex-info "Display name is required" {:field :display-name})))
  (when (db/get-user-by-email ds email)
    (throw (ex-info "Email is already taken" {:field :email})))
  (create-user ds (assoc user :role "user")))

(defn seed-admin!
  "Создать администратора при первом запуске, если пользователей нет,
   и назначить им существующие осиротевшие записи. Учётные данные берутся
   из переменных окружения GOODMOOD_ADMIN_EMAIL / GOODMOOD_ADMIN_PASSWORD
   (или из переданной env map)."
  ([ds]
   (seed-admin! ds (System/getenv)))
  ([ds env]
   (when (zero? (db/count-users ds))
     (let [email (or (get env "GOODMOOD_ADMIN_EMAIL") "admin@goodmood.local")
           password (or (get env "GOODMOOD_ADMIN_PASSWORD")
                        (throw (ex-info "GOODMOOD_ADMIN_PASSWORD env variable is required when no users exist" {})))
           admin (db/create-user! ds {:email email
                                      :password-hash (hashers/derive password)
                                      :display-name "Admin"
                                      :role "admin"})]
       (db/assign-orphan-entries! ds (:id admin))
       admin))))