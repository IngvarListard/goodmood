# Tasks: fix-push-subscription

## 1. Валидация подписки (домен)

- [x] 1.1 В `src/app/domains/push.clj` переписать `validate-subscription`: валидировать только срез `endpoint` + `keys{p256dh,auth}` (приватный хелпер `subscription-subset` через `select-keys`), лишние ключи браузера игнорировать. Проверка: REPL `(app.domains.push/validate-subscription {:endpoint "x" :keys {:p256dh "a" :auth "b"} :expirationTime nil})` → `nil`
- [x] 1.2 В `test/app/domains/push_test.clj` добавить тест: с `:expirationTime` → `nil`; без `:keys`/`:p256dh` → map ошибок (не-nil). Проверка: `clojure -M:test`

## 2. Харденинг enable-flow (фронтенд)

- [x] 2.1 В `resources/public/js/push.js` в `GMPush.enable` после `serviceWorker.ready` сначала `await reg.pushManager.getSubscription()`; если подписка есть — использовать её, иначе `subscribe`; при ошибке — существующий `showStatus`. Проверка: ручное нажатие «Включить» дважды подряд на `/settings`
- [x] 2.2 Убедиться, что `resources/public/js/push.js` не получил нового JS-поведения вне своей роли (тупой исполнитель, CSRF/данные из сервера). Проверка: визуальный diff файла

## 3. Роут тестовой отправки (backend)

- [x] 3.1 В `src/app/routes/push.clj` добавить хендлер `test-send`: guard `vapid-configured?`, `send-push!` для `user-id` из `request`, вернуть `html-response 200` с результатом (число доставок/ошибка) через `settings/push-test-result`. Проверка: REPL/юнит-вызов хендлера
- [x] 3.2 В `src/app/routes/app.clj` зарегистрировать роут `["/push/test" {:post {:handler (partial push-routes/test-send ds)}}]` рядом с `/push/subscribe`. Проверка: `clojure -M:test` (app_test грузится)

## 4. UI, CSRF и i18n

- [x] 4.1 В `src/app/views/settings.clj` изменить `push-section` на `[csrf-token configured? subscribed?]`: при `subscribed?` добавить кнопку «Отправить тестовое уведомление» (`:hx-post "/push/test"`, `:hx-target "#push-test-result"`, `:hx-swap "innerHTML"`, `:hx-headers` с `X-CSRF-Token`) и пустой `<span id="push-test-result">`; добавить `push-test-result` (hiccup-фрагмент статуса по числу доставок). Проверка: рендер страницы `/settings`
- [x] 4.2 В `src/app/views/settings.clj/page` передать `csrf` в `push-section`. Проверка: `clojure -M:test`
- [x] 4.3 В `src/app/routes/push.clj` в `section-fragment` передать `(:anti-forgery-token request)` в `settings/push-section`. Проверка: `clojure -M:test`
- [x] 4.4 В `resources/i18n/ru.edn` и `resources/i18n/en.edn` добавить ключи `:push/test-button`, `:push/test-body`, `:push/test-result` (с числом доставок), `:push/test-error`. Проверка: `clojure -M:test`

## 5. Тесты

- [x] 5.1 Создать `test/app/routes/push_test.clj` (скаффолд как `test/app/routes/notifications_test.clj`): POST `/push/subscribe` с `expirationTime` → 200, строка в `push_subscriptions`, ответ содержит статус «подписаны». Проверка: `clojure -M:test`
- [x] 5.2 В `test/app/routes/push_test.clj` добавить тест `POST /push/test` с `with-redefs` на `app.domains.push/send-push!` → фрагмент с числом доставок. Проверка: `clojure -M:test`
- [x] 5.3 Прогнать полный набор: `clojure -M:test` — все тесты проходят

## 6. Запуск приложения и финальная проверка

- [x] 6.1 Запустить приложение: `./bin/dev`, проверить health `curl http://localhost:3000/` → `OK`
- [x] 6.2 Через nREPL проверить реалистичный payload: `(app.domains.push/validate-subscription <toJSON с :expirationTime>)` → `nil`
- [x] 6.3 Через Playwright MCP на `/settings`: подписаться (или reuse), нажать «Отправить тестовое уведомление», убедиться, что показан результат (число доставок/ошибка)