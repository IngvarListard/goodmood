# Tasks: fix-mobile-ux-pass

## 1. Навигация и layout (проблемы #1, #3)

- [x] 1.1 i18n: в `resources/i18n/ru.edn` — `:nav/check-in` → «Запись», `:nav/medications` → «Мед»; в `en.edn` — «Entry», «Meds»
- [x] 1.2 `views/navigation.clj`: переставить `nav-items` в порядок `[Лента, Инсайты, Запись(=/check-in), Мед, Настройки]`
- [x] 1.3 `views/layout.clj`: мобильной обёртке панели задать `--gm-nav-h: 92px` и фиксированную высоту ряда; паддинг shell-контента → `pb-[calc(env(safe-area-inset-bottom)+var(--gm-nav-h)+0.75rem)]`
- [x] 1.4 Проверка: на 412×915 RU все 5 пунктов в один ряд, панель ≤96px, кнопка «Сохранить запись» на /check-in не перекрыта (Playwright-замер)

## 2. FAB ассистента (проблема #2)

- [x] 2.1 `views/assistant.clj`: FAB — 56×56px (`w-14 h-14`), `bottom: calc(var(--gm-nav-h) + env(safe-area-inset-bottom) + 12px)` через arbitrary-класс или style, `z-60`
- [x] 2.2 Проверка: Playwright клик по FAB на 412×915 RU проходит (панель не перехватывает события), модалка открывается

## 3. Тест-дабл AI (проблема #5)

- [x] 3.1 `domains/ai.clj`: `fake-reply` (4 ветки по correlation/advice/chat/episode-warning моделям, JSON валиден Malli-схемам) + проверка `(= "1" (env/env "GOODMOOD_FAKE_AI"))` в начале `call-chat`
- [x] 3.2 `e2e/playwright.config.ts`: `GOODMOOD_FAKE_AI: '1'` в webServer.env только при пустом `OPENROUTER_API_KEY`
- [x] 3.3 REPL-проверка: с флагом `call-chat` возвращает валидный JSON без сети, чат-путь сохраняет сообщение и отвечает

## 4. Чат: оптимистичный UI + typing (проблема #4)

- [x] 4.1 `views/ai.clj` chat-panel: hyperscript на submit — вставить юзер-бабл (textContent!) и typing-бабл («Печатает» + три animate-bounce точки по design/assistant.html) в `#chat-response`, очистить инпут, scroll вниз
- [x] 4.2 hyperscript `htmx:responseError` — удалить typing-индикатор
- [x] 4.3 Стили баблов — переиспользовать классы `chat-bubble gm-gradient` / `bg-base-300`, typing — отдельный data-testid `chat-typing`

## 5. E2E чата (проблема #5, продолжение)

- [ ] 5.1 `e2e/tests/assistant-modal.spec.ts`: новый спек без `@ai` — отправить сообщение, проверить мгновенный бабл + `chat-typing`, затем ответ (canned) и отсутствие typing
- [ ] 5.2 Прогон `npm run test:fast` — новый спек проходит без ключа

## 6. Фокус в ядре (проблема #6)

- [ ] 6.1 `views/check_in.clj`: убрать аккордеон фокуса, добавить `(range-field … "focus")` после тревоги в `soft-fields`
- [ ] 6.2 Проверка e2e-хелпера `submitEntry`: поле focus без disabled уходит в сабмит; смоук чек-ина зелёный

## 7. Единая анатомия AI-карточек (проблема #8)

- [ ] 7.1 `views/ai.clj`: хелпер `confidence-dots` (3 точки: high/medium/low, nil → nil) — замена `confidence-badge` в карточках находок
- [ ] 7.2 Единая карточка находки (шапка: иконка+тип+дот-шки+dismiss; тело: line-clamp-3 + «ещё»; действия: иконочные ghost с aria-label), применить в `ai-correlations`, `ai-advice`, `ai-novel-advice`
- [ ] 7.3 Фикс: nil-confidence не рендерит ничего (нет «Missing key»)
- [ ] 7.4 Обновить текстовые локаторы в `phase5`/`phase6` спеках (aria-label фидбек-кнопок), data-testid не трогаем

## 8. Финальная верификация

- [ ] 8.1 Полный прогон `npm run test:fast` зелёный; `npm run test` с ключом — один раз
- [ ] 8.2 Ручная проверка на 412×915 RU: лента, чек-ин, модалка ассистента (скриншоты в artifacts, без pixel-compare)
- [ ] 8.3 Убедиться, что десктоп (1280px) не изменился: sidebar, shell, FAB не перекрывает сайдбар
