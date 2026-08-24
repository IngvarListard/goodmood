# AGENTS.md

# Проект

трекер настроения при биполярном расстройстве.

# Стек

Clojure, deps.edn, ring + ring-jetty-adapter, reitit, integrant, hiccup2, htmx, hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus.

# Конвенции

- src/app/{db,routes,views,domains}/..., явные reitit-роуты, простые hiccup2-функции без сложных макросов.
- при добавлении новых зависимостей в проект убедиться, что это последняя версия пакета если не сказано другого

# Импорты

- Hiccup: всегда используй `hiccup2.core` с `:refer [html raw]`. **Не используй `hiccup.core`** — он deprecated и не эскейпит строки.
  - `html` — для рендеринга hiccup-данных в HTML (автоэскейпинг)
  - `raw` — только для сознательной вставки сырого HTML (SVG-иконки и т.п.)

# Документация

- Публичные нетривиальные функции должны иметь docstring в соответствии с CODESTYLE.md.
- Тривиальные функции (геттеры, простые хелперы) и приватные функции (`defn-`) можно без docstring.


# Codestyle and rules

Читай кодстайл непосредственно перед написанием или ревью кода
- codestyle см. в ./CODESTYLE.md
- Правила написания кода и архитектуры см ./CLOJURE-RULES.md
- Пиши любые комментарии на русском языке, а логи на английском

# Фронтенд

При работе над фронтендом используй tailwind и dailsyui классы
При реализации любых ui компонентов используй компоненты tailwind daisyui. Написание своих компонентов крайний случай

## htmx-философия

- Пиши на htmx + hyperscript. **Никакого сырого JavaScript** без острой необходимости.
  - CSRF, заголовки, обмен с сервером — через атрибуты htmx, hyperscript или скрытые поля формы.
  - Если задача не решается htmx/hyperscript — сначала задай вопрос, прежде чем писать JS.
- Фрагменты ответа сервера (HTML-фрагменты) — основной способ обновления UI. Не JSON API для фронтенда.
- Используй `hx-ext="json-enc"` для отправки JSON; сервер возвращает Hiccup2-вектор, не JSON.

## Иконки

Иконки находятся по пути ./resources/icons/. Объем иконок большой. Используй grep для поиска

# Запуск

```bash
# Запуск приложения
clj -M -m app.core

# Проверка health-check
curl http://localhost:3000/
```

# Env-переменные

| Переменная | Обязательна | Описание |
|---|---|---|
| `GOODMOOD_SESSION_SECRET` | да | Секрет для подписи cookie-сессий (`system.clj`) |
| `OPENROUTER_API_KEY` | нет | Ключ OpenRouter для AI-функций. Без него все AI-функции возвращают nil (graceful degradation) |

# Обзор фич/доменов

- **entries** (`routes/entries.clj`) — записи состояния: энергия, тревога, фокус, настроение, сон, заметки
- **feed** (`routes/feed.clj`) — главная страница: текущее состояние, AI-инсайты, polling-баннеры
- **check-in** (`routes/check_in.clj`) — пошаговый чек-ин
- **medications** (`routes/medications.clj`) — CRUD медикаментов, лог приёма, активация/деактивация
- **insights** (`routes/insights.clj`) — инсайты: контекст + советы себе, привязка к state-меткам
- **state periods** (`routes/state_periods.clj`) — marking эпизодов, группировка записей
- **notifications** (`routes/notifications.clj`) — 3 слота (утро/день/вечер), баннеры
- **ai** (`routes/ai.clj`, `domains/ai.clj`) — корреляции, ярлыки, советы, чат, предупреждения об эпизодах
- **settings** (через `routes/app.clj`) — настройки уведомлений и AI

# i18n

- `app.i18n`, функция `t` (`i18n/t :key`), локали `:ru` (по умолчанию) и `:en`
- Переводы в `resources/i18n/{ru,en}.edn`
- Локаль определяется middleware `wrap-locale` (cookie > Accept-Language > умолчание)
- При добавлении новых текстов — добавляй ключи в оба файла

# Миграции

- `resources/migrations/` — SQL-миграции формата `NNN-name.up.sql` / `NNN-name.down.sql`
- migratus, автозапуск при старте системы (`system.clj` → `:db/migrate`)
- Текущие 13 миграций: users, entries, medications, insights, notification-settings, ai-findings, ai-settings, state-periods, ai-chat-messages, episode-warnings

# AI-домен

- Внешний API: OpenRouter (`https://openrouter.ai/api/v1/chat/completions`), ключ через `OPENROUTER_API_KEY`
- Opt-in per-function: master-toggle + individual (correlations, labels, advice, novel-advice, episode-warning)
- Graceful nil: при отсутствии ключа / ошибке сети / non-200 — `call-chat` возвращает nil, AI-секции не показываются
- Модели определены в `domains/ai.clj` (`correlation-model`, `advice-model`, `chat-model`, `episode-warning-model`)
- AI-чат: при nil-ответе показывается фолбэк-сообщение (не сохраняется в БД)

# E2E-тестирование фронтенда

- При работе над фронтендом **проверяй свою реализацию через Playwright MCP** (подключён в `.opencode/opencode.json`): открывай страницы, кликай, проверяй htmx-свапы в реальном браузере. Это часть девического лупа.
- Отдельный e2e-юзер для ручной/автоматической проверки: `e2e@goodmood.test` / `e2e-test-password-123`. Создаётся идемпотентно скриптом `clojure -M -i dev/seed_e2e_user.clj`.

## Как быстро писать e2e-тесты (без лишних итераций)

Пиши тесты не по памяти, а после разведки в браузере через MCP:

1. **Разведка**: открой страницу `browser_navigate`, посмотри `browser_find`/`browser_snapshot`. Получи готовый локатор для нужного элемента через `browser_generate_locator` (включён капом `testing`).
2. **Стабильные якоря**: по возможности используй `getByRole`/`getByText`/`input[name=…]`, а не хрупкие CSS-цепочки. Учти локали (ru/en): селекторы по тексту делай regex `/Войти|Log in/`.
3. **Переиспользуй хелперы** из `e2e/helpers.ts` (`login`, `submitEntry`, `addMedication`) — там уже закрыты CSRF, `json-enc`, OOB-свапы.
4. **Микро-цикл**: в итерации гоняй один spec, а не весь набор:
   ```bash
   cd e2e && npx playwright test tests/smoke.spec.ts
   ```
5. Минимальный смоук-набор лежит в `e2e/` (Playwright Test). Полный запуск обычной CLI-командой:

```bash
cd e2e && npx playwright test
```

  - `webServer` сам стартует приложение, если оно не запущено; уже запущенный сервер переиспользуется (`reuseExistingServer: true`).
- Скриншоты и пиксельная сверка пока **не нужны** (дизайн не устоялся) — не используй их.
