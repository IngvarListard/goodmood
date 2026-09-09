(ns app.db.seed
  "Идемпотентный seed на пустой БД: админ-юзер + тестовые данные («рыба»),
   чтобы свежий подъём/деплой можно было сразу визуально проверить.
   При существующих пользователях ничего не делает (прод-данные не трогает)."
  (:require [app.db.entries :as entries]
            [app.db.insights :as insights]
            [app.db.medications :as medications]
            [app.domains.users :as users]))

(defn- days-ago
  "Дата N дней назад в формате SQLite (YYYY-MM-DD)."
  [n]
  (str (.minusDays (java.time.LocalDate/now) n)))

(defn- stamp
  "created_at «N дней назад HH:MM» в формате SQLite datetime."
  [days hh mm]
  (format "%s %02d:%02d:00" (days-ago days) hh mm))

(defn- seed-fish!
  "Тестовые данные: записи состояния за последние дни, инсайт, медикамент.
   state_label записи и инсайта совпадают (balanced), чтобы виджет инсайта
   на Ленте показывался сразу."
  [ds user-id]
  (entries/create-entry! ds
   {:user-id user-id :date (days-ago 3) :activity "Работа, прогулка"
    :effect "" :mood-score 6 :energy 5 :anxiety 4 :focus 6 :sleep-hours 7.0
    :note "Ровный день" :state-label "balanced" :created-at (stamp 3 9 30)})
  (entries/create-entry! ds
   {:user-id user-id :date (days-ago 2) :activity "Совещания"
    :effect "" :mood-score 4 :energy 3 :anxiety 7 :focus 4 :sleep-hours 5.5
    :note "Мало спал, тревожно" :state-label "anxiety" :created-at (stamp 2 10 15)})
  (entries/create-entry! ds
   {:user-id user-id :date (days-ago 1) :activity "Спорт, чтение"
    :effect "" :mood-score 7 :energy 7 :anxiety 3 :focus 7 :sleep-hours 8.0
    :created-at (stamp 1 9 0)})
  (entries/create-entry! ds
   {:user-id user-id :date (days-ago 0) :activity "Тихий вечер"
    :effect "" :mood-score 6 :energy 5 :anxiety 3 :focus 6 :sleep-hours 7.5
    :note "Спокойно" :state-label "balanced" :created-at (stamp 0 8 45)})
  (insights/create-insight! ds
   {:user-id user-id :context "Когда день ровный и есть силы"
    :category "coping"
    :advice-to-self ["Дыхание 4-7-8 пять минут" "Прогулка без телефона"]
    :identity "Я умею заботиться о себе" :state-label "balanced"})
  (medications/create-medication! ds
   {:user-id user-id :name "Препарат А" :dose 300.0 :dose-unit "мг"
    :schedule "08:00, 20:00" :sensitive 0 :notes "Тестовый препарат (рыба)"}))

(defn seed!
  "Админ + рыба при пустой БД, иначе ничего. Возвращает map админа или nil.
   Креды — из GOODMOOD_ADMIN_EMAIL / GOODMOOD_ADMIN_PASSWORD (см. users/seed-admin!)."
  [ds]
  (when-let [admin (users/seed-admin! ds)]
    (seed-fish! ds (:id admin))
    admin))
