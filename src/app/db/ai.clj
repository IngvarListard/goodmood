(ns app.db.ai
  (:require [clojure.data.json :as json]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- parse-json
  "Распарсить JSON-строку в Clojure-данные (keywords-keys map/vector).
   nil и ошибка → nil."
  [s]
  (when s
    (try
      (json/read-str s :key-fn keyword)
      (catch Exception _ nil))))

(defn- decorate-row
  "Преобразовать строку из БД: распарсить content и source_refs из JSON."
  [row]
  (when row
    (assoc row
           :content (parse-json (:content row))
           :source-refs (parse-json (:source-refs row)))))

(defn insert-finding!
  "Создать AI-находку (корреляция/ярлык/совет). content — map (JSON),
   source-refs — вектор строк/ссылок (JSON-массив), confidence — string
   или nil. Возвращает созданную строку (с распарсенными JSON-полями)."
  [ds {:keys [user-id type content confidence source-refs]}]
  (-> (jdbc/execute-one!
       ds
       (sql/format {:insert-into :ai_findings
                    :values [{:user_id user-id
                              :type type
                              :content (json/write-str content)
                              :confidence confidence
                              :source_refs (json/write-str (vec source-refs))}]
                    :returning [:*]})
       default-opts)
      decorate-row))

(defn get-findings
  "Список находок пользователя по type, только не скрытые (hidden=0),
   отсортированных по created_at DESC."
  [ds user-id type]
  (->> (jdbc/execute!
        ds
        (sql/format {:select [:*]
                     :from [:ai_findings]
                     :where [:and [:= :user_id user-id]
                             [:= :type type]
                             [:= :hidden 0]]
                     :order-by [[:created_at :desc]]})
        default-opts)
       (map decorate-row)))

(defn set-feedback!
  "Пометить находку feedback'ом и скрыть (hidden=1) без удаления.
   Ограничено user-id. Возвращает обновлённую строку или nil."
  [ds user-id id feedback]
  (-> (jdbc/execute-one!
       ds
       (sql/format {:update :ai_findings
                    :set     {:feedback feedback
                              :hidden   1}
                    :where   [:and [:= :id id] [:= :user_id user-id]]
                    :returning [:*]})
       default-opts)
      decorate-row))

(defn- default-settings
  "Настройки AI по умолчанию (все функции включены, novel advice и
   предупреждения эпизодов выключены — opt-in Фазы 7)."
  [user-id]
  {:user-id               user-id
   :master-enabled        1
   :correlations-enabled  1
   :labels-enabled        1
   :advice-enabled        1
   :allow-novel-advice    0
   :episode-warning-enabled 0})

(defn get-ai-settings
  "Прочитать настройки AI пользователя или nil (нет строки)."
  [ds user-id]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:*]
                :from   [:user_ai_settings]
                :where  [:= :user_id user-id]})
   default-opts))

(defn set-ai-settings!
  "Сохранить настройки AI пользователя (upsert по user_id).
   Принимает map {:master-enabled :correlations-enabled :labels-enabled
   :advice-enabled :allow-novel-advice :episode-warning-enabled} — значения
   0/1 (novel и episode-warning default 0). Возвращает сохранённую строку."
  [ds user-id {:keys [master-enabled correlations-enabled labels-enabled
                      advice-enabled allow-novel-advice episode-warning-enabled]}]
  (letfn [(flag [v] (if (or (nil? v) (= v "false") (= v "") (= v "0") (= v 0) (false? v)) 0 1))]
    (jdbc/execute-one!
     ds
     (sql/format {:insert-into :user_ai_settings
                  :values     [{:user_id               user-id
                                :master_enabled        (flag master-enabled)
                                :correlations_enabled  (flag correlations-enabled)
                                :labels_enabled        (flag labels-enabled)
                                :advice_enabled        (flag advice-enabled)
                                :allow_novel_advice    (flag allow-novel-advice)
                                :episode_warning_enabled (flag episode-warning-enabled)}]
                  :on-conflict :user-id
                  :do-update-set {:master_enabled        (flag master-enabled)
                                  :correlations_enabled  (flag correlations-enabled)
                                  :labels_enabled        (flag labels-enabled)
                                  :advice_enabled        (flag advice-enabled)
                                  :allow_novel_advice    (flag allow-novel-advice)
                                  :episode_warning_enabled (flag episode-warning-enabled)}
                  :returning [:*]})
     default-opts)))

;; ──────────────────────────────────────────────────────────────
;; Episode warnings (Фаза 7) — предупреждения о начале эпизода
;; ──────────────────────────────────────────────────────────────

(defn insert-warning!
  "Создать предупреждение о возможном начале эпизода.
   type: 'depressive'|'hypomanic'; pattern-description — текст-объяснение
   паттерна; confidence — число 0.0-1.0; dismissed — 0 (показывать) или 1
   (логировать «не показано», например при низкой уверенности)."
  [ds {:keys [user-id type pattern-description confidence dismissed]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :episode_warnings
                :values     [{:user_id             user-id
                              :type                type
                              :pattern_description pattern-description
                              :confidence          confidence
                              :dismissed           (or dismissed 0)}]
                :returning [:*]})
   default-opts))

(defn get-warnings
  "Активные (dismissed=0) предупреждения пользователя, последние сверху."
  [ds user-id]
  (jdbc/execute!
   ds
   (sql/format {:select [:*]
                :from   [:episode_warnings]
                :where  [:and [:= :user_id user-id]
                         [:= :dismissed 0]]
                :order-by [[:id :desc]]})
   default-opts))

(defn set-warning-feedback!
  "Пометить предупреждение feedback'ом и скрыть (dismissed=1) без удаления.
   Ограничено user-id. Возвращает обновлённую строку или nil."
  [ds user-id id feedback]
  (jdbc/execute-one!
   ds
   (sql/format {:update :episode_warnings
                :set    {:feedback  feedback
                         :dismissed 1}
                :where  [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))

(defn dismiss-warning!
  "Скрыть предупреждение (dismissed=1) без feedback. Ограничено user-id.
   Возвращает обновлённую строку или nil."
  [ds user-id id]
  (jdbc/execute-one!
   ds
   (sql/format {:update :episode_warnings
                :set    {:dismissed 1}
                :where  [:and [:= :id id] [:= :user_id user-id]]
                :returning [:*]})
   default-opts))

;; ──────────────────────────────────────────────────────────────
;; AI-чат (кэш сообщений)
;; ──────────────────────────────────────────────────────────────

(defn save-message!
  "Сохранить сообщение чата (role: user|assistant). Возвращает строку."
  [ds {:keys [user-id role content]}]
  (jdbc/execute-one!
   ds
   (sql/format {:insert-into :ai_chat_messages
                :values [{:user_id user-id :role role :content content}]
                :returning [:*]})
   default-opts))

(defn get-messages
  "Последние limit сообщений чата пользователя в хронологическом порядке
   (самое старое — первое). Пусто, если сообщений нет."
  [ds user-id limit]
  (->> (jdbc/execute!
        ds
        (sql/format {:select [:*]
                     :from [:ai_chat_messages]
                     :where [:= :user_id user-id]
                     :order-by [[:id :desc]]
                     :limit limit})
        default-opts)
       reverse
       vec))