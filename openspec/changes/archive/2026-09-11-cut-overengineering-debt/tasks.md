## 1. Мёртвый код (самый безопасный батч)

- [x] 1.1 Удалить src/app/views/placeholder.clj, test/app/views/placeholder_test.clj
- [x] 1.2 Удалить db.users/get-user-by-id, app.domains.ai/all-findings, app.i18n/t-for, арность 2 у chat-history (domains/ai.clj)
- [x] 1.3 Удалить 4 no-op ig/halt-key! метода (system.clj) и startup-warning в core.clj (-main)
- [x] 1.4 Удалить e2e/tests/__screenshot.spec.ts и e2e/tests/__dbg-locale.spec.ts
- [x] 1.5 Прогнать clj -M:test и cd e2e && npx playwright test; коммит

## 2. JSON-унификация на cheshire

- [x] 2.1 src/app/db/ai.clj: clojure.data.json → cheshire (parse-string :key-fn keyword / generate-string)
- [x] 2.2 6 route-тестов: jsonista → cheshire
- [x] 2.3 Прогнать clj -M:test; коммит

## 3. Дедуп html-response

- [x] 3.1 Создать ns src/app/routes/html.clj с html-response [status body]
- [x] 3.2 Удалить 10 приватных копий из routes/{app,auth,entries,medications,feed,check_in,insights,notifications,ai,state_periods}.clj, перейти на общий хелпер
- [x] 3.3 Прогнать clj -M:test; коммит

## 4. AI-настройки: схлопнуть бойлерплейт

- [x] 4.1 db/ai.clj set-ai-settings!: вычислить 6 флагов в let, ссылаться в values и do-update-set
- [x] 4.2 domains/ai.clj update-settings: reduce по списку ключей вместо 6 ручных coerce-enabled
- [x] 4.3 domains/ai.clj: тела *-enabled? предикатов через приватный flag-enabled?, публичные имена и докстринги сохранить
- [x] 4.4 Прогнать clj -M:test; коммит

## 5. Дедуп тестовой fixture

- [x] 5.1 Создать test/app/test_helpers.clj: with-test-db (ns-уникальный путь БД), ds, migrate! через app.db.migrate
- [x] 5.2 Заменить 17 копий fixture на test-helpers/with-test-db в test/app/{db,domains,routes}/*_test.clj
- [x] 5.3 Прогнать clj -M:test (включая параллельную изоляцию файлов); коммит

## 6. Мелкий shrink

- [x] 6.1 icons.clj: убрать valid-variants и явную проверку (load-svg уже бросает), инлайнить icon-size
- [x] 6.2 middleware.clj: инлайнить local-redirect, удалить ветку match-by-name :login в require-auth
- [x] 6.3 deps.edn :test алиас → (require 'clojure.test)(clojure.test/run-all-tests)
- [x] 6.4 Прогнать clj -M:test; коммит

## 7. Опционально (понижающий приоритет, решение при имплементации)

- [x] 7.1 Если общий util-ns появился в процессе — перенести validate-with (malli) из 3 доменов; иначе пропустить и закрыть задачу как skipped

  Skipped: в процессе появились только app.routes.html и app.test-helpers —
  не доменные утилиты, класть validate-with туда нельзя (D5: слои не
  смешиваем). Отдельный app.domains.util ради 12 строк не создаём.
