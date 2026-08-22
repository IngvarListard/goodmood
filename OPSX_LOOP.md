# OPSX_LOOP — как работать с артефактами product-vision

## Два слоя

```
VISION (медленный)                          IMPLEMENTATION (быстрый)
─────────────────────────────────           ─────────────────────────────────────────
openspec/context/discovery-*.md   ← raw     openspec/changes/<change>/
openspec/changes/product-vision/              proposal.md, design.md, tasks.md,
  proposal, design, tasks                     specs/<cap>/spec.md  ← delta-спеки
  specs/**/spec.md  ← гипотезы
```

Vision = «куда идём и почему». Implementation = «что конкретно делаем в этой фазе».

## Одна итерация = одна фаза

```
1. Выбери фазу из product-vision/tasks.md
2. /opsx-explore          ← продумать дизайн фазы, решить open question
3. /opsx-propose <описание фазы>
   — AI за один проход генерит proposal/design/specs/tasks
   — delta-спеки уточняют vision-гипотезы в финальные требования
   — альтернатива для ручного режима: `openspec new change add-<feature>` (пустой скелет)
4. openspec validate --change <name>
5. Реализуй по tasks.md (сценарии из spec.md → тесты)
6. openspec status --change <name>     ← сверка
7. openspec archive --change <name>    ← delta → openspec/specs/
8. Обнови vision:
   — tasks.md: Фаза N ✅
   — design.md / specs/<cap>/spec.md если гипотеза уточнилась
   — discovery raw если появился новый инсайт
   → loop → следующая фаза
```

## Когда что обновлять

| Ситуация | Куда |
|---|---|
| Фаза реализована, спеки подтвердились | `openspec archive` (auto-sync в `openspec/specs/`) |
| Гипотеза из vision-спеки опровергнута/уточнена | `product-vision/specs/<cap>/spec.md` + `design.md` (с `[ref: A*]`) |
| Новый инсайт от использования/интервью | `discovery-notes.md` (новые якоря `#A5…`) → обезличенно в `design.md` |
| Новая фаза/капабилит | `product-vision/tasks.md` + при необходимости новый `specs/<cap>/spec.md` |
| Закрыт open question (OQ*) | убрать из «Open Questions» в `design.md`, зафиксировать решение |

## Правила

- **Личное — только в `context/discovery-*.md`.** В `product-vision/*` обезличено. Правила — в `product-vision/design.md` Decision 10.
- **Traceability:** каждое требование несёт `[ref: A*]` → ссылка на raw-цитату.
- **Не редактируй vision-спеки в ходе имплементации напрямую** — только через archive/sync change.
- **Open questions (OQ1–OQ8)** разрешаются в своём имплементационном change, не в umbrella.

## Skills под шаги

| Skill | Шаг |
|---|---|
| `openspec-explore` | 2 |
| `openspec-propose` | 3 (основной: AI генерит change целиком) |
| `openspec new change` (CLI) | 3 (ручная альтернатива: пустой скелет) |
| `openspec-apply-change` | 5 |
| `openspec-verify-change` | 6 |
| `openspec-sync-specs` | 7 (без archive) |
| `openspec-archive-change` | 7 |

## Стартовая точка

Фаза 0 — MVP-мобильная форма. Без неё не заполняется даже основатель, весь продукт стоит. Блокирующий open question: OQ1 (состав минимальной формы).
