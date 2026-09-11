# theme-switching Specification

## Purpose
TBD - created by syncing change add-theme-switcher. Update Purpose after archive.
## Requirements
### Requirement: Theme preference is persisted in a cookie

Система SHALL сохранять выбранную тему в cookie `gm-theme` со значением `system`, `light` или `dark`. Значение по умолчанию (cookie отсутствует или невалидно) — `system`. Cookie ставится на весь путь (`path=/`), значение валидируется на сервере.

#### Scenario: Saving theme via POST /theme

- **WHEN** пользователь отправляет `POST /theme` с `theme=light` и `next=/settings`
- **THEN** сервер ставит cookie `gm-theme=light` и редиректит на `/settings`

#### Scenario: Invalid cookie falls back to system

- **WHEN** cookie `gm-theme` содержит невалидное значение (например, `purple`)
- **THEN** система обрабатывает запрос как тему `system`

### Requirement: Theme is resolved server-side before rendering

Middleware SHALL читать cookie `gm-theme`, разрешать тему и класть её в request (`:theme`). Layout SHALL рендерить `data-theme` на элементе `<html>`: для `light`/`dark` — атрибут со значением темы; для `system` — атрибут не рендерится, а в `<head>` вставляется inline-скрипт, который до первой отрисовки ставит `document.documentElement.dataset.theme` по `matchMedia('(prefers-color-scheme: dark)')`.

#### Scenario: Explicit dark theme renders on html

- **WHEN** cookie `gm-theme=dark` и пользователь открывает любую страницу
- **THEN** `<html>` имеет `data-theme="dark"` и inline-скрипт system-режима отсутствует

#### Scenario: System mode resolves via inline script before paint

- **WHEN** cookie `gm-theme=system` (или отсутствует) и пользователь открывает страницу с включённым JS
- **THEN** атрибут `data-theme` на `<html>` не рендерится
- **AND** в `<head>` присутствует inline-скрипт, ставящий `data-theme` (`dark` при совпадении `prefers-color-scheme: dark`, иначе `light`)

#### Scenario: System mode without JavaScript degrades to light

- **WHEN** cookie `gm-theme=system` и JS отключён
- **THEN** страница отображается в дефолтной light-теме CDN daisyUI

### Requirement: Login page follows the theme cookie

Страница логина SHALL рендерить тему по тому же правилу (cookie → data-theme на `<html>` / inline-скрипт для system), как и аутентифицированные страницы.

#### Scenario: Login page uses saved theme

- **WHEN** неаутентифицированный пользователь с cookie `gm-theme=dark` открывает `/login`
- **THEN** `<html>` имеет `data-theme="dark"`

### Requirement: Settings expose a theme selector

Страница настроек SHALL содержать секцию «Тема» с тремя опциями — Системная / Тёмная / Светлая (radio `theme-controller`, мгновенное превью через механизм daisyUI). Форма SHALL отправлять POST /theme (с CSRF-токеном и `next`), не через htmx-boost (полная перезагрузка). Выбранная опция SHALL быть отмечена согласно текущей теме.

#### Scenario: Theme selector shows current choice

- **WHEN** пользователь с cookie `gm-theme=light` открывает `/settings`
- **THEN** радио «Светлая» отмечен, «Системная» и «Тёмная» — нет

#### Scenario: Selecting a theme persists it and applies immediately

- **WHEN** пользователь выбирает радио «Тёмная»
- **THEN** страница мгновенно перекрашивается в тёмную тему (превью через `:has()`), форма отправляет POST /theme
- **AND** после перезагрузки страницы тема остаётся тёмной

#### Scenario: Theme form bypasses htmx boost

- **WHEN** рендерится форма выбора темы
- **THEN** форма имеет атрибут `hx-boost="false"`

### Requirement: Warm palette for both themes

Тёмная тема SHALL использовать тёплый цвет основного текста (`--color-base-content: #e8e5df`), светлая — тёплую светлую палитру (бумажный фон и тёплый тёмный текст, точные значения — в design.md D5). Оверрайды SHALL быть скоуплены селекторами `[data-theme="dark"]` / `[data-theme="light"]` в обычном `<style>`-блоке layout. Промежуточные оттенки текста SHALL получаться через `text-base-content/N` (color-mix); кастомные переменные `--color-base-content-60…85` не используются.

#### Scenario: Dark theme uses warm text color

- **WHEN** рендерится страница с `data-theme="dark"`
- **THEN** вычисленное значение `--color-base-content` равно `#e8e5df`

#### Scenario: Light theme uses warm paper palette

- **WHEN** рендерится страница с `data-theme="light"`
- **THEN** `--color-base-100` — тёплый светлый (около `#faf8f4`), `--color-base-content` — тёплый тёмный (около `#4a4238`)

### Requirement: Theme selector texts are localized

Ключи `:user/theme`, `:user/theme-system`, `:user/theme-dark`, `:user/theme-light` SHALL присутствовать в `resources/i18n/ru.edn` и `resources/i18n/en.edn`.

#### Scenario: Theme section is translated

- **WHEN** страница настроек рендерится в локали `:ru` и в `:en`
- **THEN** заголовок секции и названия трёх опций локализованы в обеих локалях