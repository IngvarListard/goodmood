(ns app.domains.medications
  (:require [app.db.medications :as db]
            [clojure.string :as str]
            [malli.core :as mc]))

(def medication-schema
  "Схема медикамента: name/dose/dose_unit/schedule обязательны,
   sensitive/notes опциональны. schedule принимает строку вида \"08:00, 20:00\".
   dose — число (строка из формы коэрсится в double через json-enc)."
  [:map
   [:name :string]
   [:dose :double]
   [:dose_unit :string]
   [:schedule :string]
   [:sensitive {:optional true} [:int {:min 0 :max 1}]]
   [:notes {:optional true} [:maybe :string]]])

(def log-schema
  "Схема лога приёма. log_date опционален — по умолчанию сегодня."
  [:map
   [:medication_id :int]
   [:scheduled_time :string]
   [:status :string]
   [:log_date {:optional true} [:string]]])

(defn- now []
  (str (java.time.LocalDateTime/now)))

(defn- today []
  (str (java.time.LocalDate/now)))

(defn- dose-equals?
  "Сравнить дозы с учётом числового типа (300 vs 300.0)."
  [a b]
  (and (some? a) (some? b)
       (= (double a) (double b))))

(defn- schedule->json
  "Преобразовать строку расписания из формы (\"08:00, 20:00\") в JSON-строку БД."
  [s]
  (let [times (->> (str/split (or s "") #",")
                   (map str/trim)
                   (remove str/blank?)
                   vec)]
    (db/serialize-schedule times)))

(defn create-medication
  "Создать медикамент: конвертирует расписание из строки в JSON,
   устанавливает active=1 и чувствительность по умолчанию 0.
   Принимает snake_case-ключи как в API-схеме."
  [ds user-id {:keys [name dose dose_unit schedule sensitive notes]}]
  (db/create-medication!
   ds
   {:user-id user-id
    :name name
    :dose dose
    :dose-unit dose_unit
    :schedule (schedule->json schedule)
    :sensitive (or sensitive 0)
    :notes notes}))

(defn get-medication
  "Достать медикамент пользователя или nil."
  [ds user-id id]
  (db/get-medication ds user-id id))

(defn list-medications
  "Список всех медикаментов пользователя (активные и неактивные)."
  [ds user-id]
  (db/get-medications-by-user ds user-id))

(defn update-medication
  "Обновить медикамент. Если доза изменилась — создать запись в
   medication_dose_changes (previous_dose → new_dose). updated_at
   устанавливается при каждом обновлении.
   Принимает snake_case-ключи как в API-схеме."
  [ds user-id id {:keys [dose schedule dose_unit sensitive notes name] :as params}]
  (let [old (db/get-medication ds user-id id)
        updated
        (db/update-medication!
         ds user-id id
         {:name name
          :dose dose
          :dose-unit dose_unit
          :schedule (schedule->json schedule)
          :sensitive (or sensitive 0)
          :notes notes
          :updated-at (now)})]
    (when (and old (not (dose-equals? (:dose old) dose)))
      (db/create-dose-change! ds {:medication-id id
                                  :user-id user-id
                                  :previous-dose (:dose old)
                                  :new-dose dose}))
    updated))

(defn deactivate-medication
  "Деактивировать медикамент (active=0), не удаляя историю."
  [ds user-id id]
  (db/deactivate-medication! ds user-id id (now)))

(defn activate-medication
  "Активировать медикамент обратно (active=1)."
  [ds user-id id]
  (db/activate-medication! ds user-id id (now)))

(defn log-intake
  "Записать приём медикамента: status=taken — с taken_at=now,
   status=skipped — без taken_at. Повторная отметка обновляет запись.
   Принимает snake_case-ключи как в API-схеме."
  [ds user-id {:keys [medication_id scheduled_time status log_date]}]
  (db/upsert-log!
   ds
   {:user-id user-id
    :medication-id medication_id
    :log-date (or log_date (today))
    :scheduled-time scheduled_time
    :status status
    :taken-at (when (= status "taken") (now))}))

(defn cancel-intake
  "Удалить запись приёма — слот возвращается в состояние «не отмечен»."
  [ds user-id medication-id scheduled-time log-date]
  (db/delete-log! ds user-id medication-id (or log-date (today)) scheduled-time))

(defn get-log
  "Достать запись приёма по слоту (или nil, если слот не отмечен)."
  [ds user-id medication-id scheduled-time log-date]
  (db/get-log ds user-id medication-id (or log-date (today)) scheduled-time))

(defn get-today-slots
  "Слоты приёма на сегодня: активные медикаменты × их расписание,
   каждый слот с текущим логом (nil — не отмечен)."
  [ds user-id]
  (let [meds (db/get-medications-by-user ds user-id)
        active (filter #(= 1 (:active %)) meds)
        logs (db/get-logs-by-date ds user-id (today))
        logs-by-key (into {}
                          (map (fn [l] [[(:medication-id l) (:scheduled-time l)] l]))
                          logs)]
    (for [m active
          t (db/parse-schedule (:schedule m))]
      {:medication m
       :scheduled-time t
       :log (get logs-by-key [(:medication-id m) t])})))