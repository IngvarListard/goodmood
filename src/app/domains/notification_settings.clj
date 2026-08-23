(ns app.domains.notification-settings
  (:require [app.db.notification-settings :as db]
            [malli.core :as mc]
            [malli.error :as me]))

(def slots
  "Три слота уведомлений."
  #{"morning" "midday" "evening"})

(def slot-settings-schema
  "Malli-схема одного слота: slot (enum), enabled (bool), time (HH:MM).
   Параметры приходят из формы значками (string), поэтому используется
   string/boolean-трансформеры."
  [:map
   [:slot [:enum "morning" "midday" "evening"]]
   [:enabled [:boolean]]
   [:time [:re #"^([01]\d|2[0-3]):[0-5]\d$"]]])

(def update-settings-schema
  "Malli-схема обновления всех трёх слотов: вектор отдельных maps."
  [:vector slot-settings-schema])

(defn validate-update
  "Валидировать параметры обновления настроек (три слота).
   Возвращает nil при успехе или map ошибок (humanized)."
  [params]
  (when-not (mc/validate update-settings-schema params)
    (-> update-settings-schema
        (mc/explain params)
        me/humanize)))

(defn- coerce-enabled
  "Привести значение enabled к булеву: nil/\"\"/\"false\"/false → false,
   любой ненулевой формат (\"on\", \"true\", 1) → true."
  [v]
  (boolean
   (and (some? v)
        (not= v false)
        (not= "false" v)
        (not= "" v)
        (not= 0 v)
        (not (re-find #"(?i)^off$|^0$|^false$" (str v))))))

(defn- get-param
  "Достать параметр формы (ключ может быть keyword или string)."
  [params k]
  (or (get params k)
      (get params (name k))))

(defn- normalize-update
  "Привести входящий params к вектору `{:slot :time :enabled}`-maps.
   Работает с двумя формами: вектор maps (JSON) или хэш-мапа с ключами
   вида time_morning/enabled_morning (query params / form)."
  [params]
  (cond
    (map? params)
    (vec (keep (fn [slot]
                 (let [time (get-param params (keyword (str "time_" slot)))]
                   (when (seq time)
                     {:slot slot
                      :time time
                      :enabled (coerce-enabled
                                (get-param params (keyword (str "enabled_" slot))))})))
               slots))

    (sequential? params)
    (vec (map #(-> %
                   (update :enabled coerce-enabled)
                   (select-keys [:slot :time :enabled]))
              params))

    :else []))

(defn get-settings
  "Вернуть настройки трёх слотов пользователя (с enabled/time)."
  [ds user-id]
  (db/get-or-default ds user-id))

(defn update-settings
  "Обновить настройки трёх слотов: валидация (malli) + upsert.
   Принимает вектор maps {:slot :time :enabled} или форму-мапу
   (time_morning / enabled_morning). Возвращает обновлённые настройки
   или бросает ExceptionInfo с :errors."
  [ds user-id params]
  (let [normalized (normalize-update params)
        errors (validate-update normalized)]
    (when errors
      (throw (ex-info "Notification settings validation failed"
                      {:errors errors})))
    (db/upsert! ds user-id normalized)))

(defn mark-summary-shown
  "Пометить вечернюю сводку показанной в указанный день."
  [ds user-id day]
  (db/set-summary-shown! ds user-id day))

(defn mark-slot-shown
  "Пометить слот показанным в указанный день."
  [ds user-id slot day]
  (db/set-slot-shown! ds user-id slot day))