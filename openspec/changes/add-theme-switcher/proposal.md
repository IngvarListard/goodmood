# Proposal: add-theme-switcher

## Why

Тема приложения захардкожена тёмной (`data-theme "dark"` на `<body>`), у пользователя нет выбора. Кроме того, тёплая палитра текста (`#e8e5df` и оттенки) из `<style>`-блока layout — мёртвый код: Tailwind-CDN не обрабатывает `@theme` без `type="text/tailwindcss"`, а при обработке переменные всё равно перекрываются `[data-theme=dark]` на `<body>` (подтверждено браузерным экспериментом). Нужен переключатель темы (системная / тёмная / светлая) в настройках и попутное восстановление тёплой палитры.

## What Changes

- Cookie `gm-theme` со значениями `system` (default) | `light` | `dark` — по аналогии с существующим `gm-locale`.
- Middleware `wrap-theme`: читает cookie, кладёт `:theme` в request.
- Роут `POST /theme`: сохраняет cookie, редиректит на `:next` (паттерн `locale-post-handler`).
- Layout: `data-theme` рендерится серверно на `<html>` вместо хардкода `"dark"` на `<body>`; для `system` — inline-скрипт (~5 строк) до первой отрисовки: `matchMedia('(prefers-color-scheme: dark)')` → `html.dataset.theme` (единственное оправданное исключение из «никакого JS»: сервер не знает тему ОС, hyperscript исполняется после первой отрисовки).
- Страница логина: тоже рендерит тему по cookie (тот же middleware её накрывает).
- Настройки: секция «Тема» — `join` из трёх `radio.theme-controller` (Системная / Тёмная / Светлая), мгновенное превью через нативный механизм daisyUI `:has()`, автосабмит формы через hyperscript, `hx-boost="false"` на форме (boost не свапает атрибуты `<html>`/`<body>`).
- Палитра: мёртвый `@theme`-блок заменяется скоупленными CSS-оверрайдами: тёплый `--color-base-content: #e8e5df` для тёмной темы; тёплая светлая пара (бумажный фон `#faf8f4…#e6e0d5`, тёплый тёмный текст `#4a4238`); промежуточные оттенки через `text-base-content/60` (color-mix) — кастомные `--color-base-content-60…85` удаляются как неиспользуемые.
- i18n: ключи `:user/theme`, `:user/theme-system`, `:user/theme-dark`, `:user/theme-light` в `resources/i18n/{ru,en}.edn`.

## Capabilities

### New Capabilities
- `theme-switching`: выбор темы (system/light/dark) с сохранением в cookie, серверное разрешение темы, рендер `data-theme` на `<html>`, inline-скрипт для system-режима, секция выбора темы в настройках, применение темы на странице логина, тёплая палитра обеих тем.

### Modified Capabilities

_(нет — ui-shell data-theme не специфицирован, изменение ляжет в новый capability)_

## Scope

- Переключатель темы в настройках (страница `/settings`, секция в user-card).
- Применение темы на странице логина (`/login`).
- Серверное разрешение темы для всех страниц через middleware.
- Тёплая палитра: тёмная (текст `#e8e5df`) и светлая (бумажный фон + тёмный тёплый текст).
- Мгновенное превью при выборе через radio.theme-controller.

## Non-goals

- Живое слежение за сменой темы ОС, пока страница открыта (listener на `matchMedia('change')`) — отложено в отдельный proposal.
- Переключатель темы в user-menu (десктоп-сайдбар) — при необходимости отдельно.
- Кастомизация прочих цветов (primary и т.д.) — только base-* палитра.
- Настройка темы через API/аккаунт (только cookie, не БД).

## Impact

- **Код**: `src/app/middleware.clj` (wrap-theme), `src/app/routes/auth.clj` (theme-post-handler), `src/app/routes/app.clj` (роут + подключение middleware), `src/app/views/layout.clj` (data-theme на html, inline-скрипт, палитра), `src/app/views/auth.clj` (тема на логине), `src/app/views/settings.clj` (секция темы), `src/app/i18n.clj` (возможно ничего — только edn), `resources/i18n/{ru,en}.edn` (новые ключи).
- **Миграции/БД**: нет.
- **Зависимости**: нет новых.
- **Риски**: boost-навигация не свапает атрибуты `<html>`/`<body>` — тема меняется только через full reload формы ( mitigated `hx-boost="false"`); блокировщики CDN (`ERR_BLOCKED_BY_CLIENT`) ломают стили целиком, включая обе темы — вне скоупа.