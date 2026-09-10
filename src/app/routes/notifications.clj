(ns app.routes.notifications
  (:require [app.domains.entries :as entries]
            [app.domains.insights :as insights]
            [app.domains.notification-settings :as ns-domains]
            [app.views.notifications :as views]
            [clojure.string :as str]
            [app.routes.html :refer [html-response]]))

(defn- user-id
  [request]
  (get-in request [:identity :id]))

(defn- today-str
  []
  (str (java.time.LocalDate/now)))

(defn- raw-state-label
  "Вернуть raw state_label строки (без префикса :state/), если задан;
   иначе вычислить rule-based из осей."
  [entry]
  (or (:state-label entry)
      (-> (entries/state-label entry) name (str/replace "state/" ""))))

(defn- latest-state-label
  "Вернуть state_label последней записи пользователя (или nil)."
  [ds user-id]
  (if-let [latest (first (entries/list-entries ds user-id))]
    (raw-state-label latest)
    nil))

(defn- current-minutes
  "Текущее локальное время в минутах с полуночи (0..1439)."
  []
  (let [now (java.time.LocalTime/now)]
    (+ (* (.getHour now) 60) (.getMinute now))))

(defn- past-slot-time?
  "Вернуть true, если текущее локальное время уже прошло время слота (HH:MM).
    Сравнение в минутах с полуночи (числовое), не строками."
  [time-str]
  (when (and time-str (re-matches #"^\d{2}:\d{2}$" time-str))
    (let [[h m] (mapv parse-long (str/split time-str #":"))
          slot-minutes (+ (* h 60) m)]
      (>= (current-minutes) slot-minutes))))

(defn pending-insight
  "GET /feed/pending-insight — фрагмент баннера «пока тебя не было».
   Показывается, если есть пропущенный слот (время слота уже прошло,
   слот включён, sentinel last_slot_shown не равен сегодня) и есть
   релевантный инсайт. Возвращает самоподдерживающийся polling-фрагмент
   с баннером внутри (или без него)."
  [ds request]
  (let [uid (user-id request)
        state-label (latest-state-label ds uid)
        insight (when state-label
                  (insights/matching-insight ds uid state-label))
        settings (ns-domains/get-settings ds uid)
        today (today-str)
        away? (some (fn [{:keys [enabled time last-slot-shown]}]
                      (and (some? time)
                           enabled
                           (past-slot-time? time)
                           (not= last-slot-shown today)))
                    settings)]
    (if (and away? insight)
      (do
        (ns-domains/mark-slot-shown ds uid "morning" today)
        (html-response 200 (views/pending-insight-fragment
                            (views/away-banner state-label insight))))
      (html-response 200 (views/pending-insight-fragment nil)))))

(defn summary-dismiss
  "POST /notifications/summary-dismiss — пометить вечернюю сводку показанной."
  [ds request]
  (ns-domains/mark-summary-shown ds (user-id request) (today-str))
  {:status 200
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body ""})

(defn settings-update
  "POST /settings/notifications — обновить настройки трёх слотов.
   Параметры приходят из htmx-формы (form-encoded) или JSON (body)."
  [ds request]
  (let [uid (user-id request)
        params (merge (:form-params request)
                      (:body-params request)
                      (:params request))]
    (try
      (ns-domains/update-settings ds uid params)
      (html-response 200 (views/saved-status))
      (catch clojure.lang.ExceptionInfo _
        (html-response 200 (views/settings-error))))))