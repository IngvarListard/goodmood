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
  "Настройки AI по умолчанию (все функции включены, novel advice выключен)."
  [user-id]
  {:user-id              user-id
   :master-enabled       1
   :correlations-enabled 1
   :labels-enabled       1
   :advice-enabled       1
   :allow-novel-advice   0})

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
   :advice-enabled :allow-novel-advice} — значения 0/1 (novel default 0).
   Возвращает сохранённую строку."
  [ds user-id {:keys [master-enabled correlations-enabled labels-enabled advice-enabled allow-novel-advice]}]
  (letfn [(flag [v] (if (or (nil? v) (= v "false") (= v "") (= v "0") (= v 0) (false? v)) 0 1))]
    (jdbc/execute-one!
     ds
     (sql/format {:insert-into :user_ai_settings
                  :values     [{:user_id              user-id
                                :master_enabled       (flag master-enabled)
                                :correlations_enabled (flag correlations-enabled)
                                :labels_enabled       (flag labels-enabled)
                                :advice_enabled       (flag advice-enabled)
                                :allow_novel_advice   (flag allow-novel-advice)}]
                  :on-conflict :user-id
                  :do-update-set {:master_enabled       (flag master-enabled)
                                  :correlations_enabled (flag correlations-enabled)
                                  :labels_enabled       (flag labels-enabled)
                                  :advice_enabled       (flag advice-enabled)
                                  :allow_novel_advice   (flag allow-novel-advice)}
                  :returning [:*]})
     default-opts)))

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