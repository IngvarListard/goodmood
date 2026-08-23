# Оркестратор — playbook автономного прохождения фаз

## Роль

Ты — оркестратор. Ты НЕ пишешь код. Ты:
1. Читаешь статус проекта
2. Диспачуешь сабагентов (Task)
3. Проверяешь результаты
4. Коммичишь
5. Ретраишь при провале
6. Зовёшь человека при неразрешимом блокере

## Предзапуск (один раз)

```
1. Прочитай openspec/changes/product-vision/tasks.md
2. Прочитай DECISIONS.md
3. Прочитай openspec/changes/product-vision/design.md (Open Questions)
4. Определи: какая фаза следующая (первый unchecked)
5. Проверь: есть ли blocking OQ для этой фазы в DECISIONS.md
   - если да → продолжай
   - если нет → СТОП, скажи человеку какой OQ не решён
```

## Главный loop (на каждую фазу)

### Шаг A — Explore (опционально, если OQ требует решения)

```
dispatch: Task(explore, <промпт из OPSX_PHASE<N>_EXAMPLE.md Шаг 1>)
  промпт должен включать:
  - контекст из spec
  - OQ из DECISIONS.md (ответ уже известен — передай как факт)
  - текущее состояние кода
получить: решение по OQ, модель данных, UI placement
сохранить: в harness/phase<N>-explore-result.md
```

### Шаг B — Design (генерация UI)

```
dispatch: Task(general, <промпт из OPSX_PHASE<N>_EXAMPLE.md Шаг 2>)
  промпт: стандартный UI-промпт из OPSX_LOOP_UI.md + конкретный экран
  формат вывода: hiccup2-вектор
проверить: рендер через dev/preview.clj (опционально, если есть время)
сохранить: в design/<component-name>.clj
```

### Шаг C — Propose

```
dispatch: Task(general, <промпт из OPSX_PHASE<N>_EXAMPLE.md Шаг 3>)
  промпт: создание change через openspec
  включить: результаты explore (A) + design (B) + spec-пути
проверить:
  openspec validate --change <change-name>
  если fail → retry ≤ 3 с feedback
```

### Шаг D — Implement (реализатор)

```
dispatch: Task(general, "реализуй change <change-name> по tasks.md")
  промпт:
  - прочитай openspec/changes/<change-name>/tasks.md
  - прочитай openspec/changes/<change-name>/specs/*/spec.md
  - прочитай CODESTYLE.md, CLOJURE-RULES.md
  - реализуй каждую задачу по порядку
  - после каждой задачи: clj-paren-repair на изменённых файлах
  - не добавляй библиотеки без вопроса (если нужна — скажи)
проверить:
  clojure -M:test
  если fail → переходим к Шагу E (test-writer), потом retry D
```

### Шаг E — Test-writer (слепой, из spec)

```
dispatch: Task(general, "напиши тесты для change <change-name>")
  промпт:
  - прочитай openspec/changes/<change-name>/specs/*/spec.md
  - НЕ читай реализацию (не открывай src/app/routes, views, domains)
  - напиши тесты по сценариям GIVEN/WHEN/THEN из spec
  - тесты должны покрывать КАЖДЫЙ сценарий из spec
  - используй существующие test-fixtures из test/app/
проверить:
  clojure -M:test
  если fail:
    - прочитать ошибки
    - если тест правильно ловит контракт-дыру → feedback в implement (D)
    - если тест неправильно понимает контракт → править тест
  retry implement ≤ 5
```

### Шаг F — Verify + Archive

```
1. openspec status --change <change-name>
   - все задачи [x]? если нет → STOP
2. openspec validate --change <change-name>
   - is valid? если нет → retry ≤ 2
3. clojure -M:test
   - все проходят? если нет → back to D
4. clj -M -m app.core (smoke)
   - приложение запускается? если нет → back to D
5. (если есть Playwright e2e для этой фазы)
   cd e2e && npx playwright test tests/phase<N>.spec.ts
   - все проходят? если нет → back to D
6. openspec archive <change-name>
   - delta-спеки синхронизированы? если fail → retry ≤ 2
```

### Шаг G — Commit + Update vision

```
1. git add -A
2. git commit -m "phase N: <change-name> (<краткое описание>)"
3. обновить openspec/changes/product-vision/tasks.md:
   - отметить все задачи фазы [x]
   - добавить: "- [x] N.X Фаза N завершена. Change: <change-name>."
4. git add openspec/changes/product-vision/tasks.md
5. git commit -m "phase N: mark complete in product-vision/tasks.md"
```

### Шаг H — Loop

```
→ вернуться к Шагу A для следующей unchecked фазы
→ если unchecked фаз нет → "Все фазы завершены. Проверь openspec doctor."
```

## Когда СТОП

| Ситуация | Действие |
|---|---|
| Blocking OQ не в DECISIONS.md | СТОП, спросить человека |
| Нужна новая библиотека | СТОП, спросить человека |
| Retry лимит исчерпан | СТОП, показать ошибку + что сделано |
| Тесты красные после 5 retry | СТОП, показать diff ошибок |
| openspec validate fail после 3 retry | СТОП, показать проблему |
| Миграция конфликтует | СТОП, показать конфликт |

## Формат промпта для сабагента (шаблон)

```
Ты — сабагент, реализующий фазу N проекта goodmood.

КОНТЕКСТ:
- Проект: трекер настроения, Clojure + deps.edn + ring + reitit +
  integrant + hiccup2 + htmx + hyperscript + Tailwind/DaisyUI +
  SQLite + next.jdbc + HoneySQL + migratus.
- Прочитай для контекста: CODESTYLE.md, CLOJURE-RULES.md,
  openspec/changes/product-vision/design.md.

ЗАДАЧА:
<конкретная задача из tasks.md>

РЕШЕНИЯ (из DECISIONS.md, не оспаривать):
<релевантные решения>

СПЕКА (контракт):
<пути к spec файлам>

ФАЙЛЫ ДЛЯ ЧТЕНИЯ:
<пути к текущему коду>

ОГРАНИЧЕНИЯ:
- Hiccup v2, DaisyUI, htmx-атрибуты в мапе.
- Кодстайл: CODESTYLE.md, CLOJURE-RULES.md.
- Не добавляй библиотеки без явного разрешения.
- Комментарии на русском, логи на английском.

ВЕРНИ:
- Краткий отчёт: что сделано, что не сделано, какие проблемы.
- Если нужен человек — скажи явно "BLOCKED: <причина>".
```

## Retry-промпт (при провале)

```
[PREVIOUS ATTEMPT FAILED]
Причина: <ошибка/test-fail/validate-fail>
Лог ошибки: <краткая выжимка>

Реализуй заново с учётом этой ошибки. Начни с исправления причины провала.
```
