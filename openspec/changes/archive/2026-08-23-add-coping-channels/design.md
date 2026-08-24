## Context

Фаза 4 roadmap'а (`product-vision`). Текущее состояние: /feed — лента с hero-карточкой (SVG-радар), виджет инсайтов под hero (`feed.clj:98`), compact-cards; /check-in — htmx-форма с редиректом на /feed при успехе (`check_in.clj:67-70`); /settings — карточка пользователя + переключатель языка. Инсайт-артефакт и rule-based подбор по `state_label` реализованы (Фаза 3).

Ключевые решения из explore-сессии (2026-08-23), зафиксированные в `product-vision/design.md` Decisions 14–16:
- **Вариант C**: in-app канал сейчас (htmx-polling + Notification API через hyperscript), полный PWA push — отдельный future change.
- **Polling, не SSE** (Decision 14.1).
- **Без таблицы notifications** — всё вычисляется из данных, поля-сторожа (Decision 14.2).
- **Soft mode** (Decision 14.4), **OOB-toast** (14.5), **баннер вечерней сводки = OQ6** (Decision 16).

## Goals / Non-Goals

**Goals:**
- Активная доставка советов: три слота (утро/день/вечер) через in-app polling + баннер «пока тебя не было» при открытии.
- Toast-подсказка после сохранения записи (OOB-swap, не прерывает flow).
- Вечерняя сводка «на завтра» (resolve OQ6) как баннер на /feed.
- Soft mode для `low`/`mixed` (митигация руминации).
- Настройки трёх слотов в /settings.
- Всё rule-based, без AI, без новых зависимостей.

**Non-Goals:**
- PWA push / service worker / web-push (future change).
- AI-советы, AI-тренд сводки (Фаза 5).
- Medication reminders.
- Серверный планировщик слотов — слоты определяются на клиенте (hyperscript localtime) + баннер «пока тебя не было» вычисляется сервером на лету; отдельный таймер в integrant НЕ вводится (YAGNI для in-app канала).

## Decisions

### D1. Доставка — polling, не SSE
`/feed` опрашивает `/feed/pending-insight` каждые 60s (`hx-trigger="every 60s"`).

- SSE держит HTTP-соединение постоянно (заряд батареи) и разрывается при сворачивании вкладки на мобилке — реалтайм превращается в «хуже polling».
- Polling = один лёгкий GET, хвоста нет, надёжнее на мобилке, не требует серверной инфраструктуры (нет состояния соединений в памяти).
- `hx-trigger="every 60s"` — из коробки htmx, без кастомного JS.

*Альтернатива (отвергнута):* SSE (`hx-sse`). Отклонена по причинам выше (батарея, разрывы, серверное состояние).

### D2. Хранение — без таблицы notifications
Все «показать при открытии» вычисляются из существующих данных: последняя запись → `state_label` → релевантный инсайт (уже есть `insights/matching-insight`).

- Таблица событий добавила бы модель, миграцию и чистильщик протухших записей — YAGNI.
- Поля-сторожа на настройках: `last_summary_date` (вечерняя сводка «не показывать сегодня») и `last_slot_shown` (баннер «пока тебя не было»).

*Альтернатива (отвергнута):* таблица `notifications` (id, user_id, type, payload, scheduled_at, delivered_at, read_at). Отклонена: дублирует данные, требует чистки, ничего не добавляет к вычисляемому подбору.

### D3. Модель данных — `user_notification_settings` (миграция 008)

```sql
CREATE TABLE user_notification_settings (
  user_id          INTEGER NOT NULL REFERENCES users(id),
  slot             TEXT NOT NULL CHECK (slot IN ('morning','midday','evening')),
  enabled          INTEGER NOT NULL DEFAULT 1,
  time             TEXT NOT NULL DEFAULT '08:00',  -- per-slot: 08:00 / 13:00 / 19:00
  last_summary_date TEXT,                           -- sentinel для вечерней сводки «показано сегодня»
  last_slot_shown   TEXT,                           -- sentinel для баннера «пока тебя не было»
  PRIMARY KEY (user_id, slot)
);
```

3 строки на пользователя, seed по умолчанию (08:00 / 13:00 / 19:00, enabled=1) при первом обращении (upsert-on-read). Два sentinel-поля живут на строке соответствующего слота (для вечерней сводки — на `evening`, для «пока тебя не было» — на слоте, который пропущен).

*Альтернатива (отвергнута):* колонки в `users` (`notif_morning_time`, `notif_morning_enabled`, ...) — раздувает users, нерасширяемо. Отдельная таблица — расширяемая, де-нормализация уместна.

### D4. Вечерняя сводка (resolve OQ6) — баннер, не страница
Баннер `alert alert-info` вверху /feed (до заголовка «Лента»):
- Триггер (сервер): `LocalTime.now >= 18:00` AND есть записи сегодня AND `last_summary_date != today`.
- Свёрнутое состояние: одна строка «Сводка на завтра готова» + кнопки «посмотреть» и «×».
- Раскрытие по тапу (hyperscript toggle).
- Контент: текущий `state_label` («Сегодня ты в состоянии "тревога"») + карточка «В прошлый раз в тревоге тебе помогло» (context + первый advice + ссылка `/insights/:id`) + напутствие «Завтра — новый день. То, что помогало раньше, поможет снова».
- Dismiss: POST `/notifications/summary-dismiss` → `last_summary_date = today` → баннер исчезает (htmx, CSRF).
- Нет релевантного инсайта: облегчённый вариант без карточки (не онбординг).

*Альтернатива (отвергнута):* отдельная страница `/summary` — плодит IA, ценность умещается в баннер.

### D5. Toast-подсказка после сохранения — OOB-swap
Поток `/check-in` сохраняется: `htmx:afterRequest successful → window.location = '/feed'`. Релевантный инсайт для введённого `state_label` возвращается в том же ответе через `hx-swap-oob="true"` во `#feed-toasts` на /feed.

- Сервер в POST `/entries` после создания записи вычисляет `state_label` (из введённых осей через `entries/state-label`) и `insights/matching-insight`; при наличии — рендерит OOB-фрагмент.
- Нет инсайта → фрагмент не рендерится (нет пустых мест, нет онбординга в тосте).
- Non-blocking, non-modal, dismissible (`_ on click add .hidden to me`).

### D6. Soft mode
- Триггер: последняя запись `state_label` ∈ `{low, mixed}`.
- /check-in: баннер `alert-info` над формой, кнопки «мягкий режим» / «полная форма». «Мягкий режим» скрывает `#soft-targets` (энергия/тревога + optional), показывает `#mood-only` (слайдер `mood_score` + сохранить). Всё через hyperscript toggle, без сырого JS. Форма по умолчанию полная (не принуждаем).
- /feed: при `low`/`mixed` виджет инсайтов рендерится выше hero (в `today-section` меняется порядок).

### D7. In-app уведомления слота
- Клиент (hyperscript на /feed): `LocalTime.now` против настроек слотов → при наступлении слота показать toast + `Notification` API (js-interop, `Notification.requestPermission` при первом слоте; если разрешение отклонено — только in-app toast, graceful fallback).
- Сервер: `/feed/pending-insight` возвращает фрагмент инсайта для текущего состояния, когда слот «пропущен» (баннер «пока тебя не было»), и очищает `last_slot_shown`. Проверка времени слотов — на клиенте; сервер не хранит расписание в памяти.

*Примечание:* Notification API через hyperscript `js` — это js-interop в атрибуте `_`, не сырой JS-файл (соблюдает правило AGENTS.md).

### D8. Настройки в /settings
Секция «Уведомления» (три строки: Утро/День/Вечер), каждый слот: `time`-input + `toggle`. Одна кнопка «Сохранить» → POST `/settings/notifications` (upsert трёх строк) → alert «Сохранено» dismissible.

*Примечание по названию:* design.md Decision 14.3 предлагал «Сводки и подсказки» + пометку «в приложении». Промпт просит «Уведомления». В этом change — «Уведомления» (как в промпте), подзаголовок снимает риск обещания push: «содержат твои собственные инсайты, а не напоминания заполнить дневник».

## Risks / Trade-offs

- [Polling-нагрузка на сервер] → Один лёгкий GET `/feed/pending-insight` раз в 60s на открытую вкладку; для персонального продукта пренебрежимо. При масштабировании — увеличить интервал.
- [Notification API не получило разрешение / iOS ограничения] → Graceful fallback на in-app toast (гипотеза: toast достаточен). iOS требует установки PWA на home screen для полноценного push — это future change, не Фаза 4.
- [In-app only = пропуск при закрытой вкладке] → Покрывается баннером «пока тебя не было» при следующем открытии /feed (вычисляется из данных, не теряется). Это осознанный trade-off варианта C.
- [Toast «В таком состоянии тебе помогало» может показаться навязчивым при каждом сохранении] → Показывается только при наличии релевантного инсайта, dismissible, non-modal. Гипотеза из discovery (A3-q3) — ценность в возврате инсайта.
- [Ложное чувство «push обещан» при названии «Уведомления»] → Подзаголовок явно говорит «в приложении»/содержимое = свои инсайты. При внедрении PWA push — название сохранится, функциональность расширится.
- [Серверный расчёт сводки на каждый /feed] → Два простых запроса (наличие записей сегодня + подбор инсайта), уже есть в `/feed` (routes/feed.clj). Дополнительной нагрузки нет.

## Migration Plan

1. Миграция 008: `CREATE TABLE user_notification_settings` (up), `DROP TABLE` (down). SQLite, обратима.
2. Seed по умолчанию при первом обращении (upsert-on-read в `db.notification-settings/get-or-default`), не в миграции — не ломает существующих пользователей.
3. Rollback: откат миграции + удаление роутов `/feed/pending-insight`, `/notifications/summary-dismiss`, `/settings/notifications`. UI-фрагменты (баннер, toast) исчезают вместе с роутами; /feed и /check-in остаются рабочими.

## Open Questions

- OQ4 (граница вмешательства) — пока открыто, Фаза 4 не финализирует: решаем по фактам использования.
- Название секции «Уведомления» vs «Сводки и подсказки» — выбранное в этом change: «Уведомления». Если на превью UX-название смущает — переименование тривиально, до archive.