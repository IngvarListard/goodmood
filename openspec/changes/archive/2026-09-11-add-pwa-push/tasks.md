# Tasks: add-pwa-push

## 1. Установимость (manifest, иконки, SW)

- [x] 1.1 `resources/public/manifest.webmanifest`: name/short_name «Good Mood», display standalone, start_url /feed, scope /, theme_color #5b5bea, background_color #0c0f17, icons 192/512 (+maskable)
- [x] 1.2 Иконки: dev-скрипт генерации (белый глиф «розы ветров» на #5b5bea, maskable safe-zone) → `resources/public/icons/{icon-192.png,icon-512.png,icon-maskable-192.png,icon-maskable-512.png}`, артефакты коммитятся
- [x] 1.3 `resources/public/sw.js`: install skipWaiting; fetch — сеть-онли, fail → минимальная страница «нет сети»; обработчики push + notificationclick (открыть/фокус /feed); без кэширования ответов
- [x] 1.4 `views/layout.clj`: `<link rel=manifest>`, `<meta theme-color>`, `<meta vapid-public-key>` (при наличии ключей), `/js/push.js` (defer) — SW регистрируется на каждой странице; проверка: неаутентифицированный GET /manifest.webmanifest и /sw.js → 200

## 2. Подписка

- [x] 2.1 deps.edn: `nl.martijndwars/web-push` 5.1.2 (BouncyCastle-версии выровнять); `dev/gen_vapid_keys.clj` (одноразовая генерация пары); env `GOODMOOD_VAPID_PUBLIC_KEY` / `GOODMOOD_VAPID_PRIVATE_KEY` (+ `.env.example`, документация в README/AGENTS при деплое)
- [x] 2.2 Миграция `003-push-subscriptions.{up,down}.sql`: push_subscriptions (id, user_id, endpoint UNIQUE, p256dh, auth, created_at)
- [x] 2.3 `app.db.push`: subscribe! (upsert по endpoint), unsubscribe!, get-subscriptions, delete-subscription!
- [x] 2.4 `app.domains.push`: валидация подписки, отправка пуша юзеру (web-push lib, 404/410 → удалить строку), vapid-configured?
- [x] 2.5 `app.routes.push`: POST /push/subscribe, POST /push/unsubscribe (auth + CSRF); регистрация в routes/app.clj
- [x] 2.6 `resources/public/js/push.js`: регистрация SW, кнопка «Включить» → requestPermission → subscribe → POST; «Выключить» → unsubscribe → POST; только user gesture, CSRF из meta
- [x] 2.7 `views/settings.clj`: секция «Push-уведомления» в блоке уведомлений: статус (вкл/выкл/недоступно без ключей), кнопка; i18n-тексты — из сервера

## 3. Доставка

- [x] 3.1 `app.system.clj`: компонент `:push/scheduler` (поток, тик 60с, halt!) — тик: enabled-слоты юзеров с прошедшим временем и last_slot_shown ≠ сегодня → пуш по подпискам + mark-slot-shown (существующий sentinel); без ключей — no-op
- [x] 3.2 Тексты пушей per slot — i18n (morning/midday/evening), без упрёков; клик по пушу → /feed
- [x] 3.3 Unit-тесты домена: тик выбирает ровно «просроченные неноказанные» слоты; отправка помечает sentinel (второй тик не шлёт); 410 удаляет подписку; без подписок — ничего
- [x] 3.4 REPL-проверка: subscribe fake-подписки → тик шлёт (fake send), sentinel выставлен; in-app away-баннер за этот слот не приходит

## 4. i18n и верификация

- [x] 4.1 i18n ru/en: названия/тексты пушей, кнопки «Включить уведомления»/«Выключить», статусы секции, страница «нет сети»
- [x] 4.2 e2e: manifest/sw отдаются 200 без сессии; в head есть manifest+theme-color; подписка (фейк push-менеджера если нужно) сохраняется; кнопки секции переключаются; прогнать `npm run test:fast` и полный прогон
- [x] 4.3 Ручная проверка на Android (Chrome → Установить → иконка → standalone без адресной строки; пуш по слоту) — отмечается владельцем
- [x] 4.4 Обновить деплой-заметки: VAPID env в NAS `.env`, рестарт нужен (новый компонент system.clj); коммит
