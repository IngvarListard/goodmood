## 1. Серверная механика (cookie → middleware → роут)

- [x] 1.1 `middleware.clj`: добавить `wrap-theme` — чтение cookie `gm-theme`, валидация (не `light`/`dark` → `system`), `assoc :theme` в request
- [x] 1.2 `routes/auth.clj`: добавить `theme-post-handler` (set-cookie `gm-theme`, redirect на `next`, копия `locale-post-handler`)
- [x] 1.3 `routes/app.clj`: роут `POST /theme` рядом с `/locale`; подключить `wrap-theme` в стек middleware рядом с `wrap-locale`
- [x] 1.4 Проверить в REPL: middleware разрешает `:theme` из cookie, handler ставит cookie и редиректит

## 2. Layout: рендер темы и палитра

- [x] 2.1 `layout.clj`: перенести `data-theme` с `<body>` на `<html>`; для `:theme` из request рендерить `"light"`/`"dark"`, для `system` — не рендерить атрибут
- [x] 2.2 `layout.clj`: при `system` вставить inline-скрипт в `<head>` до отрисовки (`matchMedia('(prefers-color-scheme: dark)')` → `documentElement.dataset.theme`), с комментарием на русском о причине исключения
- [x] 2.3 `layout.clj`: убрать `<body data-theme="dark">`; удалить мёртвый `@theme`-блок со всеми `--color-base-content-60…85`
- [x] 2.4 `layout.clj`: добавить обычный `<style>` со скоупленной палитрой — `[data-theme="dark"]` → тёплый `#e8e5df`; `[data-theme="light"]` → бумажная пара из design.md D5
- [x] 2.5 `auth.clj` (login page): рендерить тему по `:theme` на `<html>` (та же функция разрешения темы, что в layout — вынести в layout/head при необходимости)

## 3. Настройки: секция выбора темы

- [x] 3.1 `settings.clj`: секция «Тема» в user-card (под языковым переключателем, с divider) — `join` из трёх `radio.theme-controller` (values `system`/`dark`/`light`), checked из `:theme`
- [x] 3.2 Форма: `hx-boost="false"`, CSRF-токен, hidden `next`; автосабмит через hyperscript `on change send submit to the closest <form/>` к радио
- [x] 3.3 `resources/i18n/ru.edn` и `en.edn`: ключи `:user/theme`, `:user/theme-system`, `:user/theme-dark`, `:user/theme-light`

## 4. Верификация в браузере (Playwright MCP)

- [x] 4.1 Тёмная тема: `data-theme="dark"` на html, вычисленный `--color-base-content` = `#e8e5df`, тёплые оттенки `/60…/85` читаемы
- [x] 4.2 Светлая тема: через селектор переключиться на light — перекраска мгновенная (превью), после перезагрузки тема сохранилась; фон тёплый бумажный, контраст текста читаемый (подкрутить значения при необходимости)
- [x] 4.3 System-режим: без cookie эмуляция `prefers-color-scheme: dark|light` (Playwright `colorScheme`) даёт соответствующую тему; inline-скрипт срабатывает до отрисовки (нет вспышки)
- [x] 4.4 `/login`: применяет тему из cookie для всех трёх значений
- [x] 4.5 Boost-консистентность: после переключения темы переходы по меню дают страницы той же темы (атрибут на html актуален после full reload)
- [x] 4.6 e2e-смоук: `cd e2e && npx playwright test tests/smoke.spec.ts` зелёный после всех изменений