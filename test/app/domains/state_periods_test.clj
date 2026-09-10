(ns app.domains.state-periods-test
  (:require [app.domains.state-periods :as sp]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private ds-atom (atom nil))

(def user-id 1)

(def other-user-id 2)

(use-fixtures :each #(test-helpers/with-test-db :state-periods ds-atom %))

(defn- columns-of
  [table]
  (->> (jdbc/execute! @ds-atom [(str "PRAGMA table_info(" table ")")]
                      {:builder-fn rs/as-unqualified-maps})
       (mapv :name)))

(deftest test-state-periods-migration-schema
  (testing "миграция 011 создаёт state_periods с нужными колонками"
    (is (= ["id" "user_id" "label" "started_at" "ended_at" "notes" "created_at"]
           (columns-of "state_periods"))))
  (testing "entries.state_period_id существует (миграция 006)"
    (is (contains? (set (columns-of "entries")) "state_period_id")))
  (testing "allow_novel_advice добавлен в user_ai_settings со значением по умолчанию 0"
    (is (contains? (set (columns-of "user_ai_settings")) "allow_novel_advice"))
    (jdbc/execute! @ds-atom ["INSERT INTO user_ai_settings (user_id) VALUES (?)" user-id])
    (let [row (first (jdbc/execute! @ds-atom ["SELECT allow_novel_advice FROM user_ai_settings WHERE user_id=?" user-id]
                                    {:builder-fn rs/as-unqualified-maps}))]
      (is (= 0 (:allow_novel_advice row)) "novel advice выключен по умолчанию"))))

(deftest test-ai-chat-messages-migration-schema
  (testing "миграция 012 создаёт ai_chat_messages (кэш чата) с role user/assistant"
    (is (= ["id" "user_id" "role" "content" "created_at"]
           (columns-of "ai_chat_messages")))
    (is (thrown? java.sql.SQLException
                 (jdbc/execute! @ds-atom
                                ["INSERT INTO ai_chat_messages (user_id, role, content) VALUES (?,?,?)"
                                 1 "system" "x"]))
        "роль вне user/assistant отклоняется")))

(deftest test-start-period-creates-open-period
  (testing "start-period создаёт период со started_at и без ended_at"
    (let [period (sp/start-period @ds-atom user-id {:label "спад"})]
      (is (some? (:id period)) "период создан")
      (is (some? (:started-at period)) "started_at заполнен")
      (is (nil? (:ended-at period)) "ended_at пуст у открытого периода")
      (is (= "спад" (:label period))))))

(deftest test-validate-start-rejects-empty-label
  (testing "пустой label отвергается валидацией"
    (is (some? (sp/validate-start {:label ""})) "пустой label — ошибка валидации")
    (is (some? (sp/validate-start {})) "отсутствует label — ошибка")
    (is (nil? (sp/validate-start {:label "спад" :notes "x"})) "валидные данные проходят")))

(deftest test-start-period-invalid-label-throws
  (testing "start-period бросает исключение на невалидных данных"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sp/start-period @ds-atom user-id {:label ""})))))

(deftest test-active-period-returns-nil-when-none
  (testing "active-period возвращает nil, когда нет открытых периодов"
    (is (nil? (sp/active-period @ds-atom user-id)))))

(deftest test-start-period-closes-previous-active
  (testing "начало нового периода закрывает предыдущий (один активный одновременно)"
    (let [p1 (sp/start-period @ds-atom user-id {:label "спад"})
          p2 (sp/start-period @ds-atom user-id {:label "подъём"})]
      (is (nil? (:ended-at p2)) "новый период открыт")
      (let [periods (sp/list-periods @ds-atom user-id)]
        (is (= 2 (count periods)) "созданы оба периода")
        (is (= (:id p2) (:id (first periods))) "свежайший период — новый")
        (is (some? (:ended-at (second periods))) "предыдущий период закрыт")
        (is (nil? (:ended-at (first periods))) "новый период открыт"))
      (let [active (sp/active-period @ds-atom user-id)]
        (is (= (:id p2) (:id active)) "активен только новый период")))))

(deftest test-close-period-fills-ended
  (testing "close-period заполняет ended_at открытого периода"
    (let [period (sp/start-period @ds-atom user-id {:label "спад"})
          closed (sp/close-period @ds-atom user-id (:id period))]
      (is (some? (:ended-at closed)) "ended_at заполнен")
      (is (nil? (sp/active-period @ds-atom user-id)) "после закрытия открытых периодов нет"))))

(deftest test-close-period-scoped-per-user
  (testing "close-period не закрывает чужой период"
    (sp/start-period @ds-atom user-id {:label "спад"})
    (let [period (sp/active-period @ds-atom user-id)
          res-foreign (sp/close-period @ds-atom other-user-id (:id period))]
      (is (nil? res-foreign) "чужой пользователь не закрывает чужой период")
      (is (= (:id period) (:id (sp/active-period @ds-atom user-id)))
          "период остаётся открытым у владельца"))))

(deftest test-list-periods-most-recent-first
  (testing "list-periods возвращает периоды свежайшие первыми и только свои"
    (sp/start-period @ds-atom user-id {:label "первый"})
    (sp/close-period @ds-atom user-id (:id (sp/active-period @ds-atom user-id)))
    (sp/start-period @ds-atom user-id {:label "второй"})
    (sp/start-period @ds-atom other-user-id {:label "чужой"})
    (let [mine (sp/list-periods @ds-atom user-id)]
      (is (= 2 (count mine)) "видны только свои периоды")
      (is (= "второй" (:label (first mine))) "свежайший период первый"))))

(deftest test-period-with-notes
  (testing "период сохраняет opциональные notes"
    (let [period (sp/start-period @ds-atom user-id {:label "тревожная неделя" :notes "плохо спал"})]
      (is (= "тревожная неделя" (:label period)))
      (is (= "плохо спал" (:notes period))))))