## Why

Фаза 4 продуктового roadmap'а (`product-vision`). Фазы 0–3 дали MVP-форму, медикаменты, розу ветров с rule-based ярлыками, /feed как landing и инсайт-артефакт с подбором по `state_label`. Но ядро продуктовой ценности — «доступ к инсайтам именно в нужный момент» — до сих пор пассивно: инсайт виден только в виджете /feed, когда пользователь сам туда зашёл. Нет активной доставки советов (утро/день/вечер), нет on-demand «что делать сейчас», нет подготовки к следующему дню, нет мягкого режима для плохих состояний.

Фаза 4 вводит каналы доставки советов по варианту C (решение из explore-сессии 2026-08-23): **in-app канал сейчас** (htmx-polling каждые 60s + Notification API через hyperscript), полный PWA push — отдельный future change. Решения зафиксированы в `product-vision/design.md` Decision 14–16 (2026-08-23), включая resolve OQ6 (формат вечерней сводки). UI-скетчи пяти экранов в `openspec/changes/product-vision/ui-sketches/{what-now-button,check-in-insight-toast,evening-summary-banner,soft-mode-banner,notification-settings}.clj`.

`[ref: A3-q3, A4-q1, OQ6]`

## What Changes

- **Кнопка/механика «что мне делать сейчас»**: виджет инсайтов на /feed уже виден постоянно под hero (Decision 15) — отдельная кнопка НЕ создаётся; виджет и есть on-demand механика. Подтверждено в explore.
- **Toast-подсказка при заполнении** (`/check-in` → `/feed`): после успешной отправки записи релевантный инсайт для введённого `state_label` возвращается через OOB-swap как dismissible `alert-info` toast. Non-blocking, не modal (Decision 14.5).
- **Вечерняя сводка «на завтра»** (resolve OQ6): баннер вверху /feed после 18:00 при наличии записей сегодня, раскрывается по тапу, dismissible на день. Контент: текущий `state_label` + релевантный инсайт + мягкое напутствие (Decision 16).
- **In-app уведомления** через polling `/feed` каждые 60s (не SSE — надёжнее на мобилке, не держит соединение); «пока тебя не было» баннер при открытии /feed (Decision 14.1, 14.2).
- **Soft mode**: при `state_label` последней записи = `low`/`mixed` — предложение мягкого режима на /check-in (только `mood_score`) + подъём виджета выше hero на /feed (Decision 14.4).
- **Настройки уведомлений**: секция в /settings, три слота (утро/день/вечер) с временем и вкл/выкл, сохраняются в новой таблице `user_notification_settings` (Decision 14.3).
- **No-pressure**: все каналы несут ценность (инсайты/советы), не «вы пропустили день» (spec `coping-channels` Requirement 8).

## Capabilities

### New Capabilities
- `coping-channels`: активная доставка советов и инсайтов — in-app polling-уведомления (утро/день/вечер), вечерняя сводка «на завтра», мягкий режим в плохих состояниях, настройки каналов. Без PWA push на этой фазе (future), без AI (Фаза 5).

### Modified Capabilities
- `entries-ui`: soft mode на /check-in (баннер предложения + упрощённая форма только с `mood_score`), виджет инсайтов на /feed поднимается выше hero в плохом состоянии, вечерняя сводка-баннер вверху /feed.
- `entries-data`: добавляется таблица `user_notification_settings` (настройки трёх слотов) — новая модель, но не меняет схему `entries`; связь с пользователем через `user_id`.

## Scope

- **In scope:** миграция 008 (`user_notification_settings`), домен/БД-слой настроек, polling-эндпоинт `/feed/pending-insight`, OOB-toast подсказки при сохранении записи, баннер вечерней сводки, soft mode (баннер на /check-in + подъём виджета), секция настроек в /settings, i18n-ключи, Notification API через hyperscript (js-interop), graceful fallback на in-app toast.
- **Out of scope:** полный PWA push (service worker + Push API + серверный web-push) — отдельный future change; AI-генерация советов и AI-анализ тренда (Фаза 5); medication reminders (интеграция с расписанием медикаментов — отдельный запрос); смена языка каналов не требуется.

## Non-goals

- **PWA push / service worker** — Non-goal на этой фазе (вариант C). Уведомления только когда вкладка открыта; пропуск при закрытой вкладке покрывается баннером «пока тебя не было» при следующем открытии.
- **AI-советы и AI-тренд сводки** — Фаза 5. Фаза 4: всё rule-based (подбор по `state_label` exact match).
- **Medication reminders** — отдельный future-запрос, не в этой фазе.
- **Геймификация / стрик / «вы пропустили день»** — запрещено (spec Requirement 8, product-vision Non-goals).
- **Пуш вне приложения / background delivery** — Non-goal этой фазы.

## Impact

- **Миграция:** `008-add-notification-settings.up.sql` / `.down.sql` (CREATE TABLE `user_notification_settings`).
- **Новые файлы:**
  - `src/app/db/notification_settings.clj` — CRUD настроек слотов.
  - `src/app/domains/notification_settings.clj` — malli-схема, валидация, upsert.
  - `src/app/routes/notifications.clj` — handlers: pending-insight, summary-dismiss, settings-update.
  - `src/app/views/notifications.clj` — фрагменты: OOB-toast подсказки, баннер сводки, баннер «пока тебя не было», секция настроек.
- **Изменяемые файлы:**
  - `src/app/routes/app.clj` — роуты `/feed/pending-insight`, `/notifications/summary-dismiss`, `/settings/notifications`.
  - `src/app/routes/check_in.clj` — после создания записи возвращать OOB-toast.
  - `src/app/routes/feed.clj` — вычислять состояние для сводки/баннера «пока тебя не было».
  - `src/app/views/feed.clj` — вставить баннер сводки, подъём виджета в soft mode.
  - `src/app/views/check_in.clj` — баннер мягкого режима + toggle формы.
  - `src/app/views/settings.clj` — секция «Уведомления» (три слота).
  - `src/app/system.clj` — (опционально) таймер для слотов, если решено server-side; иначе только клиентский polling.
  - `src/app/i18n.clj` (или ресурс) — ключи для баннеров, подсказок, настроек, soft mode.
- **Зависимости:** новые библиотеки не добавляются. Notification API — через hyperscript (js-interop), не через сырой JS-файл. Если потребуется серверный планировщик слотов — использовать встроенный `java.util.concurrent.ScheduledExecutorService`, без внешней зависимости.
- **Спецификации:** `openspec/specs/coping-channels/spec.md` и `openspec/specs/entries-ui/spec.md` обновятся при archive; до этого — delta в `openspec/changes/add-coping-channels/specs/`.

## Acceptance Criteria

- GIVEN пользователь в плохом состоянии (последняя запись `state_label=low`) открывает `/check-in` THEN видит баннер «Тебе сейчас может быть непросто» с выбором «мягкий режим»/«полная форма», полная форма остаётся доступной
- GIVEN пользователь включает мягкий режим THEN форма показывает только `mood_score`, энергия/тревога и опциональные блоки скрыты
- GIVEN пользователь сохраняет запись на /check-in WHEN есть релевантный инсайт для введённого `state_label` THEN после редиректа на /feed виден dismissible toast «В таком состоянии тебе помогало: …»
- GIVEN пользователь без релевантного инсайта WHEN сохраняет запись THEN toast НЕ показывается (нет пустых мест, нет онбординга в toast)
- GIVEN локальное время ≥ 18:00, сегодня есть записи, сводка ещё не показана TODAY THEN при открытии /feed виден баннер «Сводка на завтра готова», раскрывается по тапу, dismissible на день
- GIVEN пользователь закрыл вкладку утром WHEN открывает /feed позже THEN видит баннер «пока тебя не было, был утренний инсайт» (вычисляется из данных, не теряется)
- GIVEN пользователь открывает /settings THEN видит три слота (утро/день/вечер) с временем и toggle; при сохранении — alert «Сохранено», dismissible
- GIVEN пользователь отключает вечерний слот THEN вечерние in-app баннеры не приходят, утренние/дневные продолжают работать
- GIVEN пользователь не заполнял записи сегодня WHEN наступает слот THEN уведомление содержит инсайт/совет, не «вы пропустили день» `[ref: A2-q3]`
- GIVEN Notification API не дало разрешение WHEN приходит слот THEN система показывает in-app toast (graceful fallback) и не падает
- GIVEN `openspec validate --change add-coping-channels` THEN структура валидна