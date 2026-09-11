# Design: add-pwa-push

## Context

Приложение уже даёт: HTTPS (reverse proxy DSM), 90-дневную cookie-сессию, мобильный UI с нижней навигацией, статики без авторизации (`wrap-resource "public"`). Спека `coping-channels` явно откладывала «full PWA push» в future change — этот чендж его реализует. Head сейчас полностью на CDN (htmx/hyperscript/Chart.js/Tailwind browser/daisyUI/шрифты). Интегрант уже управляет жизненным циклом; фоновые потоки есть (`future` для AI), но шедулера нет.

Границы владельца: Android-first, standalone + иконка + push, **без офлайна**; при успехе продукта — переписывание на другой стек (Dart), значит платформу не закапывать глубоко: минимум хаков, максимум стандартных API.

## Goals / Non-Goals

Goals: установимость на рабочий стол (standalone); push в момент слота; подписка/отписка; чистка мёртвых подписок; graceful degradation без VAPID.

Non-Goals: офлайн-кэш и офлайн-записи; iOS; персональный payload (инсайты); vendor CDN; store-обёртки.

## Decisions

### D1. Установимость: manifest + минимальный SW, без кэша

```
manifest.webmanifest  — name "Good Mood", display standalone,
                        start_url /feed, scope /, theme_color #5b5bea,
                        background_color #0c0f17, icons 192/512 + maskable
sw.js                 — install: skipWaiting; fetch: сеть-онли,
                        при fail → минимальная страница «нет сети»
```

- Standalone убирает адресную строку — это и есть «не в рамке».
- SW нужен в этом чендже только как гарантия installability на всех движках Chrome; кэширование ответов осознанно **не делается** (офлайн отклонён владельцем): лента персональная, кэш = риск чужих/несвежих данных. Пуш-обработчик (`push`, `notificationclick`) появится в sw.js в этом же чендже — он обязан быть в SW.
- Fallback-страница «нет сети» — не офлайн-режим, а честная замена браузерной «ERR_INTERNET_DISCONNECTED».

### D2. Иконки: роза ветров, генерируются один раз, коммитятся в репо

192/512 PNG + maskable (safe-zone 80%): белый глиф «розы ветров» (как радар на ленте) на фоне primary #5b5bea. Генерация — разовый скрипт в `dev/` (не в пайплайне сборки), артефакты коммитятся. `theme-color` meta в layout — тот же #5b5bea (окрашивает статус-бар/тайтлбар Android в обеих темах).

### D3. Подписка: Push API → POST /push/subscribe → push_subscriptions

```
push_subscriptions(id, user_id, endpoint UNIQUE, p256dh, auth, created_at)
```

- Разрешение запрашивается только по жесту юзера (кнопка в /settings, секция «Push-уведомления»): «Включить» → `Notification.requestPermission()` → `pushManager.subscribe({userVisibleOnly: true, applicationServerKey: VAPID-public})` → POST подписки (CSRF как у остальных форм).
- Публичный VAPID-ключ приезжает в `<meta name="vapid-public-key">` в layout (публичное по определению).
- «Выключить» → `unsubscribe()` + POST /push/unsubscribe. Шедулер при 404/410 от push-сервиса удаляет подписку из БД (юзер снял сайт с телефона).
- Без env-ключей: секция показывает «уведомления недоступны», шедулер no-op (паттерн graceful nil как у AI).

### D4. Доставка: интегрант-компонент-шедулер, reuses слоты и sentinel

```
:push/scheduler {:connection (ig/ref :db/connection)}
поток: тик раз в 60с (halt! в ig/halt! — как у сервера)
тик: для каждого enabled-слота юзера, где локальное время ≥ времени слота
     и last_slot_shown ≠ сегодня:
       send web-push всем подпискам юзера (текст слота из i18n)
       mark-slot-shown (existing sentinel — он же гейтит in-app баннер)
```

- Часы — серверное локальное время, как у in-app баннеров (`LocalTime/now`), рассинхрона нет.
- Общий sentinel решает дублирование: подписанный юзер получает пуш и больше не видит «пока тебя не было» при следующем открытии; неподписанный — прежний in-app опыт без изменений.
- Ровно-в-момент не обещаем: тик 60с → пуш приходит в пределах минуты после времени слота. Для напоминания достаточно.
- Тексты пушей — фиксированные i18n per slot («Утро началось. Загляни в своё состояние» / «Как проходит день?» / «Сводка на завтра готова») — без чувства вины, в духе «No pressure channels». Инсайты в payload — non-goal v1 (вычисление per user при отправке можно добавить позже без смены транспорта).
- Клик по пушу → фокус существующего окна или открыть /feed (SW `notificationclick`).

### D5. Зависимость и ключи

- `nl.martijndwars/web-push` 5.1.2 (JVM, VAPID + aes128gcm; BouncyCastle уже в classpath через buddy — версии jdk18on выровнять).
- VAPID-ключи: одноразовый `dev/gen_vapid_keys.clj` (generate → печатает пару) → `GOODMOOD_VAPID_PUBLIC_KEY` / `GOODMOOD_VAPID_PRIVATE_KEY` в `.env` (gitignored) и NAS `.env`. Приватный ключ хранится base64url, нигде не логируется.

### D6. Браузерный glue: push.js — сырой JS как radar.js

SW-регистрация, requestPermission, subscribe, base64url-конвертация, POST подписки — браузерные API, из hyperscript/htmx их не решить. Паттерн тот же, что `radar.js`: сервер рендерит данные/тексты (мета-тег, i18n-кнопки), JS — тупой исполнитель. `push.js` регистрирует SW сразу (нужен installability и до подписки).

## Risks / Trade-offs

- [Шедулер тикает в лоб (60с) вместо точного планирования] → простота; нагрузка нулевая (один SELECT по индексу); для семьи достаточно
- [Пуш отправлен, но телефон офлайн — push-сервис сам ретраит до суток] → стандартное поведение FCM; свои ретраи не нужны
- [Sentinel: пуш в момент слота подавляет «away»-баннер с инсайтом] → осознанный выбор: уведомление доставлено; при отключении подписки баннер возвращается сам
- [Tanwind-CDN и прочие CDN остаются] → в scope не входит; SW их не кэширует — офлайн не притворяется работающим
- [Смена ключей VAPID → все подписки невалидны] → редкая операция; 404/410 чистится автоматически, юзеры подписываются заново

## Migration Plan

`003-push-subscriptions` (up + down). Откат кода — revert; откат миграции — down.sql. Существующие юзеры ничего не замечают, пока сами не включат уведомления.

## Open Questions

- Нет: границы (Android, без офлайна, i18n-тексты) подтверждены владельцем в explore-сессии.
