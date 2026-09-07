# Tasks: redesign-feed-assistant

## 1. Подготовка

- [ ] 1.1 Создать ветку `redesign-feed-assistant`; grep по `resources/icons/heroicons` точных имён иконок (light-bulb, x-mark, paper-airplane, chat-bubble, sparkles, calendar) и зафиксировать соответствия FontAwesome → heroicons
- [ ] 1.2 Добавить i18n-ключи в `resources/i18n/{ru,en}.edn`: assistant/title, assistant/subtitle, assistant/close, assistant/disclaimer (проверить существующие `:ai/chat-*`, переиспользовать где можно)

## 2. Хелперы и модалка ассистента

- [ ] 2.1 В `layout.clj` добавить CSS-хелперы `gm-accent-stripe` (левая градиентная полоса 3–4px) и soft-glow для FAB/pill — через CSS-переменные, рядом с существующими `gm-*`
- [ ] 2.2 Создать `src/app/views/assistant.clj`: FAB (круглая, `gm-gradient`, иконка чат+искра, `data-testid="assistant-fab"`, `hx-get /ai/chat → #assistant-body` + `on htmx:afterRequest call #assistant-modal.showModal()`) и bottom-sheet модалку на нативном `<dialog class="modal modal-bottom">` (`data-testid="assistant-modal"`, id `assistant-modal`): drag-handle, шапка (заголовок/подзаголовок/крестик), fallback-плейсхолдер в `#assistant-body`
- [ ] 2.3 Контент чата в модалке: переиспользовать `GET /ai/chat` — история через `chat-response` (data-testid `chat-response` сохранён), закреплённый composer (flex-none внизу, круглая кнопка отправки `paper-airplane`), постоянная строка дисклеймера над composer; убрать confirm-гейт `chat-disclaimer` из рендера (эндпоинт POST /ai/chat/disclaimer остаётся в коде)
- [ ] 2.4 Подключить FAB + модалку в `layout.clj` (после `<main>`, csrf из request); проверить graceful-рендер без токена
- [ ] 2.5 Перезагрузить неймспейсы в REPL, проверить рендер модалки и hx-цепочку «клик FAB → фрагмент → showModal» в браузере (Playwright MCP)

## 3. Верх ленты

- [ ] 3.1 `state_periods.clj`: обернуть `active-indicator` в карточку ленты (pill `gm-gradient` + дата под ним + outline «Закрыть период» справа); start-banner — той же карточной системой
- [ ] 3.2 `notifications.clj` `summary-banner`: убрать `alert-info` и встроенный insight-блок → тонкий однострочный баннер (тёмная поверхность, `gm-accent-stripe`, иконка, «посмотреть» через collapse, крестик dismiss с существующим POST)
- [ ] 3.3 `insights.clj` `feed-widget` + `ai.clj` `ai-advice`: карточка совета с `gm-accent-stripe`, лампочкой, крестиком dismiss (hyperscript `remove me`), collapse «посмотреть полностью» для длинного текста
- [ ] 3.4 `feed.clj`: удалить `chat-launcher`, `#ai-chat-open` и `fab` («+»); проверить, что верх ленты собирается в порядок период → сводка → совет → hero/timeline
- [ ] 3.5 Проверка в REPL + браузере: нет сплошных заливок, дублей совета, даты/времени суммируются корректно; вечерний сценарий (≥18:00) — мокнуть или проверить существующим путём

## 4. Обе темы и косметика

- [ ] 4.1 Пройтись по новым компонентам с `data-theme=light`: нет хардкод-hex поверхностей/текстов, всё читаемо (скрин-проверка Playwright MCP)
- [ ] 4.2 `medications.clj`: pill-кнопки «Принял/Пропустил» и карточки — привести к системе (цвета через семантические токены success/warning, без новых hex); поведение не менять
- [ ] 4.3 `settings.clj`: карточка профиля/тумблеры — выровнять с системой (DaisyUI `toggle`, `join` если нужно); поведение не менять
- [ ] 4.4 `/insights`, `/check-in`: быстрый аудит соответствия (tabs-box vs tabs-boxed в daisyUI 5 — проверить фактический рендер); точечные правки только если выпадает из системы

## 5. Тесты и приёмка

- [ ] 5.1 Обновить/добавить e2e: FAB `assistant-fab` открывает модалку, отправка сообщения работает, инлайн-чата и FAB «+» нет; хелпер `openAssistant()` в `e2e/helpers.ts`
- [ ] 5.2 Прогнать существующий смоук-набор (`cd e2e && npx playwright test tests/smoke.spec.ts`) — падения от исчезнувших элементов починены
- [ ] 5.3 Полный прогон e2e (`cd e2e && npx playwright test`); запуск приложения `clj -M -m app.core` + health-check
- [ ] 5.4 Критерии приёмки: ни одной сплошной заливки крупной площади акцентом; ровно одна FAB на ленте; чат только через модалку; интерактив — классы DaisyUI/hyperscript без сырого JS; разметка Hiccup v2

## 6. Завершение

- [ ] 6.1 Коммиты мелкими шагами по разделам (2 → 3 → 4 → 5), логи на английском
- [ ] 6.2 `openspec validate --change redesign-feed-assistant` перед архивацией
