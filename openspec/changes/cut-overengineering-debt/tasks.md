## 1. Мёртвый код (самый безопасный батч)

- [ ] 1.1 Удалить src/app/views/placeholder.clj, test/app/views/placeholder_test.clj
- [ ] 1.2 Удалить db.users/get-user-by-id, app.domains.ai/all-findings, app.i18n/t-for, арность 2 у chat-history (domains/ai.clj)
- [ ] 1.3 Удалить 4 no-op ig/halt-key! метода (system.clj) и startup-warning в core.clj (-main)
- [ ] 1.4 Удалить e2e/tests/__screenshot.spec.ts и e2e/tests/__dbg-locale.spec.ts
- [ ] 1.5 Прогнать clj -M:test и cd e2e && npx playwright test; коммит

## 2. JSON-унификация на cheshire

- [ ] 2.1 src/app/db/ai.clj: clojure.data.json → cheshire (parse-string :key-fn keyword / generate-string)
- [ ] 2.2 6 route-тестов: jsonista → cheshire
- [ ] 2.3 Прогнать clj -M:test; коммит

## 3. Дедуп html-response

- [ ] 3.1 Создать ns src/app/routes/html.clj с html-response [status body]
- [ ] 3.2 Удалить 10 приватных копий из routes/{app,auth,entries,medications,feed,check_in,insights,notifications,ai,state_periods}.clj, перейти на общий хелпер
- [ ] 3.3 Прогнать clj -M:test; коммит

## 4. AI-настройки: схлопнуть бойлерплейт

- [ ] 4.1 db/ai.clj set-ai-settings!: вычислить 6 флагов в let, ссылаться в values и do-update-set
- [ ] 4.2 domains/ai.clj update-settings: reduce по списку ключей вместо 6 ручных coerce-enabled
- [ ] 4.3 domains/ai.clj: тела *-enabled? предикатов через приватный flag-enabled?, публичные имена и докстринги сохранить
- [ ] 4.4 Прогнать clj -M:test; коммит

## 5. Дедуп тестовой fixture

- [ ] 5.1 Создать test/app/test_helpers.clj: with-test-db (ns-уникальный путь БД), ds, migrate! через app.db.migrate
- [ ] 5.2 Заменить 17 копий fixture на test-helpers/with-test-db в test/app/{db,domains,routes}/*_test.clj
- [ ] 5.3 Прогнать clj -M:test (включая параллельную изоляцию файлов); коммит

## 6. Мелкий shrink

- [ ] 6.1 icons.clj: убрать valid-variants и явную проверку (load-svg уже бросает), инлайнить icon-size
- [ ] 6.2 middleware.clj: инлайнить local-redirect, удалить ветку match-by-name :login в require-auth
- [ ] 6.3 deps.edn :test алиас → (require 'clojure.test)(clojure.test/run-all-tests)
- [ ] 6.4 Прогнать clj -M:test; коммит

## 7. Опционально (понижающий приоритет, решение при имплементации)

- [ ] 7.1 Если общий util-ns появился в процессе — перенести validate-with (malli) из 3 доменов; иначе пропустить и закрыть задачу как skipped
