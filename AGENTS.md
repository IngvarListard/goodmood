# AGENTS.md

# Проект

трекер настроения при биполярном расстройстве.

# Стек

Clojure, deps.edn, ring + ring-jetty-adapter, reitit, integrant, hiccup2, htmx, hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus.

# Конвенции

- src/app/{db,routes,views,domains}/..., явные reitit-роуты, простые hiccup2-функции без сложных макросов.
- при добавлении новых зависимостей в проект убедиться, что это последняя версия пакета если не сказано другого

# Clojure REPL-driven разработка

- Правки `.clj/.cljc/.cljs` — в первую очередь через структурные тула clojure-mcp (`clojure_edit`, `clojure_edit_replace_sexp`, `paren_repair`); нативные edit/write/apply_patch для кложурных файлов — только как запаска.
- Сначала прототип в REPL (`clojure_eval`), потом сохранение в файл, потом перезагрузка неймспейса (`:reload`) и перепроверка.
- Мелкие шаги, частые коммиты в отдельной ветке.

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
# Dev-старт: приложение (:3000) + nREPL (:7890), переменные из .env (если есть)
./bin/dev

# Без .env-файла (переменные только из окружения)
clj -M:dev

# Проверка health-check
curl http://localhost:3000/
```

Локальная конфигурация живёт в `.env` (gitignored, образец — `.env.example`).
Реальный env всегда сильнее `.env` (`app.env`). В контейнере `.env` не
используется — переменные приходят через compose `env_file`.

## REPL-луп без рестартов

Правки view/route-неймспейсов подхватываются перезагрузкой по nREPL (:7890),
без перезапуска процесса:

```clojure
(require 'app.views.feed :reload)  ; и т.д. по изменённым неймспейсам
```

Рестарт нужен только для: правок system.clj, app.core.clj, deps.edn, миграций.

## REPL-гипотезы в живом процессе

Работающая система доступна по nREPL через `app.core/system` (integrant map).
Используй это, чтобы проверять гипотезы без e2e-прогонов и поднимать данные
для визуальных проверок:

```clojure
(require 'app.core)
;; живой ds для доменных вызовов:
(def ds (get app.core/system :db/connection))
(def uid (:id (app.db.users/get-user-by-email ds "e2e@goodmood.test")))

;; гипотезы на реальных данных: что сматчится, что вернёт домен?
(app.domains.insights/matching-insight ds uid "anxiety")

;; сеанс данных для визуальной проверки: создать записи/периоды доменными
;; функциями → открыть страницу в браузере → скриншот (быстрее e2e)

;; локали и тексты с параметрами:
(binding [app.i18n/*locale* :ru] (app.i18n/t :insights/widget-title {:state "тревога"}))

;; подмена недетерминизма (например AI-вызова) для граничных сценариев:
;; (with-redefs [app.domains.ai/call-chat (fn [& _] "фейковый ответ")] ...)
```

Паттерн «превью фрагмента»: view-функция с фейковыми данными →
`(hiccup2.core/html ...)` → standalone html (head из `app.views.layout/head`)
→ скриншот через playwright — без навигации по приложению.

Важно: всё, что пишешь через живой `ds`, оседает в dev-БД
(`resources/goodmood.db`) — для чистых прогонов прогоняй reset-скрипты из `dev/`.

# Env-переменные

Загружаются через `app.env`: реальный env > `.env`-файл в корне (только для
локальной разработки; в контейнере — compose `env_file`).

| Переменная | Обязательна | Описание |
|---|---|---|
| `GOODMOOD_SESSION_SECRET` | да | Секрет для подписи cookie-сессий (`system.clj`) |
| `GOODMOOD_ADMIN_EMAIL` | нет | Email админа при seed на пустой БД (default `admin@goodmood.local`) |
| `GOODMOOD_ADMIN_PASSWORD` | да при пустой БД | Пароль админа при seed на пустой БД |
| `GOODMOOD_PORT` | нет | Порт HTTP-сервера (default `3000`) |
| `GOODMOOD_DB_PATH` | нет | Путь к SQLite-файлу (default `resources/goodmood.db`, в контейнере `/data/goodmood.db`) |
| `OPENROUTER_API_KEY` | нет | Ключ OpenRouter для AI-функций. Без него все AI-функции возвращают nil (graceful degradation) |

# Деплой

Сборка и деплой на Synology DS224+ (`deploy/`):

```bash
# Одноразовая настройка NAS (каталоги, .env, compose)
NAS_HOST=ssh://<ssh-user>@<nas-lan-ip>:37132 ./deploy/nas-setup.sh

# Каждый деплой (спросит пароль sudo один раз)
NAS_HOST=ssh://<ssh-user>@<nas-lan-ip>:37132 ./bin/deploy.sh
```

- Пайплайн deploy.sh: uberjar → docker build → ssh stop → бэкап БД
  (держим 10 в `/volume1/docker/goodmood/backups`) → `docker save | ssh sudo docker load`
  → compose up → health-check по `localhost:32710`.
- Снаружи: reverse proxy DSM, `https://<public-host>` →
  `http://localhost:32710` (порт выбран случайно, слушает только localhost).
- Секреты прода — `/volume1/docker/goodmood/.env` (chmod 600), локальная копия
  — `deploy/nas.env` (gitignored).
- Откат кода: `NAS_HOST=ssh://<ssh-user>@<nas-lan-ip>:37132 ./bin/rollback.sh <тег>`
  (без аргумента — покажет теги; с `--data` — ещё и restore БД из бэкапа тега).
- Откат данных: остановить контейнер → restore `goodmood.db*` из последнего
  пред-деплойного подкаталога `backups/<тег>/` → up.
- Контейнер: non-root (uid 10001), `-Xmx384m` (NAS с 2 ГБ RAM), HEALTHCHECK
  по GET /.

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
- migratus, автозапуск при старте системы (`system.clj` → `:db/migrate`);
  упавшая миграция не даёт контейнеру подняться — deploy.sh ловит это health-check'ом
- Baseline: `001-init` (squash прежних 13 миграций, 2026-09); git-история сохраняет исходники

## Правила (после первого деплоя — обязательны)

1. **Append-only**: уже задеплоенная миграция — read-only. Правка/удаление старых файлов запрещены.
2. Каждый новый `.up.sql` — в паре с рабочим `.down.sql`, пишутся вместе.
3. Деструктив (drop/rename/type change) — только новой миграцией, с переносом данных при необходимости.
4. Перед коммитом проверять подъём с нуля: `rm resources/goodmood.db* && ./bin/dev` — миграции применяются на пустой БД, seed создаёт админа и рыбу.

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
5. Минимальный смоук-набор лежит в `e2e/` (Playwright Test). Команды прогона:

```bash
cd e2e
npm run test:fast   # без AI-спеков (~2.5 мин) — основной цикл разработки
npm run test:ai     # только AI-спеки (phase5/6/7, помечены @ai)
npm run test        # всё (~6 мин) — только перед коммитом/архивацией
```

  - AI-спеки зависят от вывода LLM и флакают — не вгоняй их в каждый цикл, полный прогон делай один раз в конце задачи.
  - `webServer` сам стартует приложение, если оно не запущено; уже запущенный сервер переиспользуется (`reuseExistingServer: true`). Для AI-тестов приложение должно быть запущено с `OPENROUTER_API_KEY`.
- Скриншоты и пиксельная сверка пока **не нужны** (дизайн не устоялся) — не используй их.
