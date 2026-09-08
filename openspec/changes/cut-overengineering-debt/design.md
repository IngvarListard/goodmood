## Context

Аудит over-engineering (explore-сессия, Sep 2026) прошёл весь tree: `src/` (~6.7k строк), `test/` (~4.4k), `e2e/` (~1.5k), ресурсы. Итог — ~600 строк на удаление/дедуп при нулевом изменении внешнего поведения. Решения владельца зафиксированы: `register-user` остаётся; иконки и user-id-дедуп отозваны; JSON-ветка create-entry остаётся как тест-контракт.

Слои db → domain → routes → views — устоявшийся паттерн проекта, его не трогаем. Каждый батч изменений должен проверяться полным прогоном тестов (`cd e2e` — отдельно для e2e).

## Goals / Non-Goals

Goals:
- Удалить мёртвый код и дубли из списка находок (ledger в proposal.md)
- Сделать зависимости явными (убрать опору на транзитивы data.json/jsonista)
- Дедуп с сохранением внешнего поведения: тесты зелёные до и после каждого батча

Non-Goals:
- Рефакторинг слоёв, ассеты (SVG), muuntaja/malli-коерция, корректность/безопасность

## Decisions

### D1: Дедуп html-response — один хелпер в `app.routes.html` (новый мини-ns)
10 копий идентичны (проверено ревью: status/headers/body, без расхождений типа HX-Trigger).
- Вариант A (выбран): `app.routes.html/html-response [status body]` — нейтральное место, не создаёт циклов (только hiccup2).
- Вариант B: положить в `app.middleware` — отвергнуто: middleware про request-обработку, не про рендер ответа.
- Вариант C: в `app.routes.app` — отвергнуто: routes/app и так — композиционный корень, все route-ns его уже требуют → риск циклических зависимостей.

### D2: Тестовый fixture — `app.test-helpers` ns с `with-test-db`
17 копий fixture идентичны (tmp-path, ds-atom, приватный migrate!, delete → migrate → reset → close).
- Хелпер экспортирует: `ds` (deref-able), `with-test-db` fixture, `migrate!`.
- Тест-файлы дописывают свои пути под tmp: уникальный suffix на ns (иначе параллельные файлы конфликтуют) — параметр `test-helpers/with-test-db` принимает путь или использует имя ns.
- Приватный `migrate!` в каждом тесте заменяется на `app.db.migrate/migrate!` — та же обёртка уже есть в src.

### D3: JSON — cheshire везде
`db/ai.clj` требует `clojure.data.json` (не объявлен в deps — транзитив, класспаcс подтверждён `clojure -Spath`); 6 route-тестов требуют `jsonista` (транзитив от muuntaja). Оба меняем на cheshire (уже объявлен, используется в 4 ns). API-разница минимальна: `json/read-str` → `cheshire/parse-string` с `:key-fn keyword`, `json/write-str` → `cheshire/generate-string`.
- Альтернатива (объявить оба явно) — отвергнута: два JSON-парсера в кодовой базе без причины.

### D4: AI-настройки — вычислять флаги один раз + хелпер `flag?`
- `db/ai.clj set-ai-settings!`: 6 флагов повторены в values и do-update-set → вынести в `let` и ссылаться.
- `domains/ai.clj update-settings`: 6 ручных `(coerce-enabled (or (get k) (get "k")))` → reduce по списку ключей.
- `*-enabled?` предикаты остаются публичными (используются снаружи), тела через приватный `(flag-enabled? settings k)` — докстринги сохраняем.

### D5: validate-бойлерплейт — общий хелпер в `app.domains.util`? Нет — локально в каждом
3 сайта `when-not validate → explain → humanize` (insights, notification_settings, state_periods). Правило трёх выполнено, но выносить в отдельный ns ради 12 строк — спорно; при имплементации: если у `app.routes.html` (D1) и `app.test-helpers` (D2) уже появились ns — класть не туда (слои не смешиваем). Решение: пока оставить как есть, резать только если общий ns всё же появится. Понижающий приоритет.
  (пересмотрено: задача оставлена в tasks как опциональная, см. tasks)

### D6: deps.edn тестовый алиас — `clojure.test/run-all-tests`
Вместо явного списка 21 require + 21 run-tests аргумент: `:main-opts ["-e" "(require 'clojure.test)(clojure.test/run-all-tests)"]`. В `test/` только тесты — фильтрация не нужна.

## Risks / Trade-offs

- [Дедуп fixture меняет семантику тайминга (fixture :each)] → путь БД на ns-уникальный; полный прогон тестов до/после
- [Удаление «мёртвых» fn может сломать невидимые вызовы (рефлексия, REPL-привычки)] → все находки проверены grep'ом по src+test+dev; тесты после каждого батча
- [Замена JSON-либ меняет edge-поведение парсинга] → в проекте парсится только вывод OpenRouter и собственные write-str; тесты db/ai покрывают round-trip
- [run-all-tests тянет порядок прогонов] → порядок ns не значим, fixtures изолируют БД

## Migration Plan

Батчи в порядке возрастания риска (см. tasks): мёртвый код → JSON-унификация → дедуп html-response → AI-флаги → fixture-дедуп → deps.edn алиас → e2e-мусор. После каждого батча — полный тест-прогон; коммит на батч. Откат — revert коммита, миграций данных нет.

## Open Questions

- Нет. Все спорные пункты сняты с владельцем в explore-сессии.
