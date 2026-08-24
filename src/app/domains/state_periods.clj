(ns app.domains.state-periods
  (:require [app.db.state-periods :as db]
            [malli.core :as mc]
            [malli.error :as me]))

(def start-period-schema
  "Malli-схема начала периода: label — непустая строка, notes — опционально."
  [:map
   [:label [:string {:min 1}]]
   [:notes {:optional true} [:maybe :string]]])

(defn validate-start
  "Валидировать параметры начала периода. Возвращает nil при успехе или
   map ошибок (humanized)."
  [params]
  (when-not (mc/validate start-period-schema params)
    (-> start-period-schema
        (mc/explain params)
        me/humanize)))

(defn start-period
  "Начать период состояния: label — непустой текст, notes — опционально.
   Перед созданием закрывает текущий активный период (одновременно открыт
   только один). started_at — текущее время. Возвращает созданный период
   или бросает ExceptionInfo с :errors при невалидных данных."
  [ds user-id {:keys [label notes]}]
  (let [errors (validate-start {:label label :notes notes})]
    (when errors
      (throw (ex-info "Period validation failed" {:errors errors})))
    (when-let [active (db/get-active-period ds user-id)]
      (db/close-period! ds user-id (:id active)))
    (db/create-period! ds {:user-id user-id
                           :label (clojure.string/trim label)
                           :notes notes})))

(defn close-period
  "Закрыть период (заполнить ended_at). Ограничено user-id; закрывает только
   открытый период. Возвращает обновлённый период или nil."
  [ds user-id id]
  (db/close-period! ds user-id id))

(defn active-period
  "Активный период пользователя или nil."
  [ds user-id]
  (db/get-active-period ds user-id))

(defn list-periods
  "Список периодов пользователя, свежайшие первые."
  [ds user-id]
  (db/get-periods ds user-id))