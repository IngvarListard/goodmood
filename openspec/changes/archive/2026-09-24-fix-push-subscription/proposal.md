# Proposal: fix-push-subscription

## Why

Web push в приложении не работает вообще. Подтверждённая в REPL первопричина:
`app.domains.push/validate-subscription` валидирует подписку по malli-схеме
`subscription-schema` с `[:map {:closed true} ...]`, а браузерный
`PushSubscription.toJSON()` присылает лишний ключ `expirationTime`. `:closed true`
его отклоняет:

```clojure
(validate-subscription {:endpoint "x" :keys {:p256dh "a" :auth "b"} :expirationTime nil})
;; => {:expirationTime [disallowed key]}
```

Как следствие `POST /push/subscribe` всегда отвечает 400, строка в
`push_subscriptions` не появляется, и ни один пуш никогда не отправляется. Вторая
проблема — повторное нажатие «Включить»: `pushManager.subscribe()` при уже
существующей подписке бросает `InvalidStateError`, а фронтенд не пытается
переиспользовать имеющуюся подписку.

Дополнительно нет способа проверить доставку вручную: после исправления нужно
убедиться, что пуш реально доходит, не дожидаясь слота.

## What Changes

- **fix (валидация)**: `validate-subscription` валидирует только необходимый
  срез подписки (`endpoint`, `keys{p256dh,auth}`), лишние безобидные ключи
  браузера (минимум `expirationTime`) игнорируются, а не отклоняются.
- **fix (фронтенд)**: `resources/public/js/push.js` в enable-flow сначала
  спрашивает `pushManager.getSubscription()` и переиспользует существующую
  подписку вместо повторного `subscribe()`; ошибки показываются в существующей
  статус-строке.
- **feature (тестовая отправка)**: новый аутентифицированный CSRF-защищённый
  `POST /push/test` отправляет пуш текущему юзеру и возвращает число доставок
  или ошибку; кнопка «Отправить тестовое уведомление» в секции
  «Push-уведомления» на `/settings`, видна только при наличии подписки.
- Новые i18n-ключи в `resources/i18n/ru.edn` и `resources/i18n/en.edn`.
- Обновлённое требование `Push subscription lifecycle` в домене `pwa`.

## Capabilities

### New Capabilities
<!-- нет новых -->

### Modified Capabilities
- `pwa`: требование `Push subscription lifecycle` расширяется — валидация
  принимает безобидные лишние ключи браузера; повторное включение
  переиспользует существующую подписку; появляется ручная тестовая отправка
  пуша.

## Impact

- `src/app/domains/push.clj` — `validate-subscription` / `subscription-schema`
- `src/app/routes/push.clj` — новый хендлер `test-send`
- `src/app/routes/app.clj` — регистрация роута `POST /push/test`
- `src/app/views/settings.clj` — `push-section` (кнопка теста, контейнер статуса)
- `resources/public/js/push.js` — enable-flow (reuse существующей подписки)
- `resources/i18n/ru.edn`, `resources/i18n/en.edn` — тексты кнопки/статуса
- `test/app/domains/push_test.clj`, `test/app/routes/push_test.clj` — тесты
- Затрагивает пользователей: подписка на push и тестовая отправка на `/settings`

## Scope

- Исправить валидацию подписки так, чтобы payload
  `PushSubscription.toJSON()` принимался.
- Сделать повторное включение push идемпотентным (reuse подписки).
- Добавить ручную тестовую отправку пуша из настроек.
- Сохранить graceful degradation без VAPID-ключей и существующий unsubscribe.

## Non-goals

- Часовой пояс планировщика и серверные часы — отдельная операционная задача,
  не входит в этот чендж.
- iOS (Safari/PWA push) не поддерживается.
- Отдельная страница истории уведомлений.
- Изменения обработки payload в `sw.js`, если это не окажется строго
  необходимым.