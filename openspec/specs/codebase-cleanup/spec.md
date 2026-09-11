# codebase-cleanup Specification

## Purpose
TBD - created by syncing change cut-overengineering-debt. Update Purpose after archive.
## Requirements
### Requirement: Кодовая база не содержит мёртвого кода

Функции, namespace'ы и ветки без вызовов (проверено grep'ом по src+test+dev и тестами) SHALL быть удалены, а не «оставлены на будущее». Спекулятивные заглушки без живых вызовов не размещаются в production-коде.

#### Scenario: Мёртвый namespace удалён

- **WHEN** в кодовой базе есть namespace, требуемый только собственным тестом (app.views.placeholder)
- **THEN** namespace и его тест удалены, роуты продолжают отвечать на все маршруты навигации

#### Scenario: Мёртвые функции удалены

- **WHEN** публичная функция не имеет вызовов вне собственного определения (db.users/get-user-by-id, app.domains.ai/all-findings, app.i18n/t-for)
- **THEN** функция удалена, полный прогон тестов зелёный

#### Scenario: No-op обработчики удалены

- **WHEN** integrant-ключ не держит ресурс, требующий остановки
- **THEN** соответствующий ig/halt-key! метод отсутствует, система стартует и останавливается штатно

### Requirement: Один хелпер вместо копий при правиле трёх и выше

Идентичные вспомогательные формы, повторённые в 3+ файлах, SHALL быть вынесены в один общий хелпер. Однострочные дубли (цена координации выше цены копии) и ассеты под это правило не подпадают.

#### Scenario: html-response дедуплицирован

- **WHEN** route-неймспейс рендерит HTML-ответ
- **THEN** он использует общий хелпер app.routes.html/html-response, приватных копий в route-файлах нет, все HTTP-ответы сохраняют статус и Content-Type

#### Scenario: Тестовая fixture дедуплицирована

- **WHEN** тест-файлу нужна изолированная SQLite-БД с миграциями
- **THEN** он использует fixture из app.test-helpers с ns-уникальным путём БД, приватных копий migrate!/with-test-db в тест-файлах нет, прогоны каждого файла изолированы

### Requirement: Зависимости объявлены явно

Код SHALL требовать только зависимости, объявленные в deps.edn. Опора на транзитивные библиотеки (clojure.data.json, jsonista) не допускается — используется уже объявленный cheshire.

#### Scenario: Единый JSON-парсер

- **WHEN** src- или test-код парсит/генерирует JSON
- **THEN** используется cheshire, require'ы clojure.data.json и jsonista отсутствуют, тесты round-trip AI-findings зелёные

### Requirement: Конфигурация тестов минимальна

Явные перечисления тест-неймспейсов в конфигурации SHALL быть заменены на автоматический прогон (clojure.test/run-all-tests), когда каталог test/ содержит только тесты.

#### Scenario: Тестовый алиас без списка

- **WHEN** запускается clj -M:test
- **THEN** прогоняются все тесты каталога test/ без явного списка ns в deps.edn