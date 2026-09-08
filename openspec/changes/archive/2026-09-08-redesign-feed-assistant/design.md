# Design: redesign-feed-assistant

## Context

Предыдущая волна редизайна (`redesign-tokens`, `redesign-feed`, `redesign-check-in`, `redesign-insight-form`) уже ввела систему токенов: палитры в `layout.clj` (`[data-theme=dark|light]`), хелперы `gm-gradient`, `gm-range`, `gm-input`, `gm-badge-*`, PageShell (`max-w-lg`), bottom-nav. Поэтому этот изменение — не «редизайн всего», а достройка двух зон: верха ленты и точки входа в чат, плюс косметика.

Референсы: `design/lentagem.html` (лента) и `design/assistant.html` (модалка). Оба написаны на Tailwind-CDN + FontAwesome с хардкод-hex — напрямую не переносимы, адаптируются по композиции, не по цветам.

Инварианты, которые нельзя ломать: Hiccup v2 + htmx/hyperscript (без сырого JS), CSRF через скрытые поля/json-enc, i18n через `app.i18n/t`, обе темы из токенов, guardrail'ы чата (crisis-детекция, graceful-fallback).

## Goals / Non-Goals

**Goals**: три компактные карточки вверху ленты; единственная FAB → bottom-sheet модалка ассистента; дедупликация совета; все поверхности через токены.

**Non-Goals**: бизнес-логика, эндпоинты (кроме вывода из использования `/ai/chat/disclaimer`), миграции, десктоп-перепланировка, жестовые анимации, радар/week-chart (меняется только обёртка).

## Decisions

### D1. Модалка — глобальная, в layout, на нативном `<dialog>` (DaisyUI `modal`)

FAB и модалка рендерятся в `layout.clj` (новый `app.views.assistant`), а не внутри `feed.clj` — модалка доступна с любой страницы, где рисуется FAB, и не пересоздаётся при htmx-свапах контента. Позиционирование снизу — DaisyUI `modal-bottom` (в v5 есть) + свои классы высоты; закрытие — нативное поведение dialog (Esc, клик по backdrop через `modal-backdrop`), как уже сделан `med-modal`. Альтернативы: drawer (heavy, второй механизм закрытия), absolute-div + hyperscript (нестабильные свапы, свой scrim) — отклонены.

### D2. Контент чата — переиспользование `GET /ai/chat`, без новых эндпоинтов

FAB: `hx-get /ai/chat → #assistant-body`, `on htmx:afterRequest call #assistant-modal.showModal()`. Внутри — существующий контракт: история рендерится `chat-response` (data-testid сохраняется), отправка `POST /ai/chat` свапает `#chat-response`. Панель теряет инлайн-обёртку (`chat-panel` переименовывается по сути в контент модалки). Альтернатива — отдельный эндпоинт под модалку отклонена: дублирует контракт.

### D3. Дисклеймер — постоянная строка, confirm-гейт уходит

Строка над composer рендерится всегда; `chat-disclaimer`-блок и POST `/ai/chat/disclaimer` из UI выпадают (эндпоинт остаётся в коде до чистки). Это изменение спеки (delta в `ai-assistant`), а не только CSS: прежнее требование — гейт с подтверждением.

### D4. Верх ленты — композиция из трёх карточек на `bg-base-200`

Карточка периода: `sp/active-indicator` оборачивается в карточку, pill остаётся на `gm-gradient`, дата — под pill, кнопка outline справа. Сводка: `summary-banner` теряет `alert-info` и встроенный insight-блок — остаётся одна строка + «посмотреть» (раскрывает сводный текст через collapse, без совета) + dismiss (существующий POST сохраняется). Совет: `feed-widget` получает стиль карточки совета (левая полоса `gm-gradient` шириной 3–4px через absolute-div, лампочка, dismiss hyperscript'ом `remove me`, collapse для длинного текста). Дубль убран со стороны сводки — advice-card единственный источник совета.

### D5. Акцентные полосы и glow — хелперы, не inline-классы

Полоса-акцент и soft-glow (FAB, pill) добавляются как `gm-accent-stripe` / расширение `gm-gradient` в `<style>` layout.clj рядом с существующими хелперами. Причина: browser-сборка Tailwind CDN не генерирует произвольные варианты от CSS-переменных — тот же опыт, что с `gm-input` (см. комментарий в layout.clj).

### D6. Иконки — heroicons через `app.icons/svg`

FontAwesome-иконки макетов заменяются по смыслу: лампочка `light-bulb`, крестик `x-mark`, самолётик — `paper-airplane`, искра в FAB — комбинация `chat-bubble` + sparkles (heroicons), дата — `calendar`. Перед использованием — grep по `resources/icons/heroicons` на точные имена файлов.

### D7. E2E-обновление вместо регрессии

Смоук-тесты, завязанные на кнопку «Чат» и FAB «+», обновляются на FAB ассистента (`data-testid` на FAB и модалке) — разведка локаторами через Playwright MCP по AGENTS.md, хелперы `e2e/helpers.ts` расширяются `openAssistant()`.

## Risks / Trade-offs

- [D1: глобальный рендер модалки в layout тянет csrf в layout на страницах без форм] → csrf уже приходит в `layout` через request; модалка рендерит форму только с токеном, без токена — без формы (graceful, как head).
- [D2: afterRequest-цепочка «получил фрагмент → открыл dialog» хрупка к ошибкам сети] → FAB с fallback: если фрагмент не пришёл, модалка открывается с плейсхолдером загрузки (декларативный fallback-контент внутри dialog).
- [D3: пользователи, привыкшие к гейту, потеряют «явное согласие»] → дисклеймер теперь постоянный и всегда виден — юридически/этически не слабее; спека обновлена.
- [D4: dismiss совета — клиентский, возвращается после перезагрузки] → осознанный компромисс (состояние dismiss'ов — отдельная задача; сейчас так живут away/hint).
- [Glow-эффекты макета на светлом фоне выглядят слабее] → допустимо: glow — акцент тёмной темы, на светлой просто мягче; функционально ничего не ломает.

## Migration Plan

1. Ветка → компоненты (assistant.clj) → layout → feed/notifications/state_periods → косметика medications/settings → i18n-ключи → E2E.
2. Откат — revert коммитов; эндпоинты не менялись, миграций нет, схема БД не тронута.

## Open Questions

- Нужен ли persist dismiss'а карточки совета (сейчас sessionless)? — решим по ощущениям после внедрения.
- `POST /ai/chat/disclaimer`: удалить сразу или оставить до чистки? — по умолчанию оставляем (Non-goal).
