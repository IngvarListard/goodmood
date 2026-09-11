(ns app.views.layout
  (:require [app.env :as env]
            [app.i18n :as i18n]
            [app.views.assistant :as assistant]
            [app.views.navigation :as navigation]
            [app.views.user-menu :as user-menu]
            [hiccup2.core :refer [raw]]))

(def htmx-src "https://unpkg.com/htmx.org@2.0.10/dist/htmx.min.js")
(def json-enc-src "https://unpkg.com/htmx.org@2.0.10/dist/ext/json-enc.js")
(def hyperscript-src "https://unpkg.com/hyperscript.org@0.9.93/dist/_hyperscript.min.js")

;; Chart.js (UMD, pinned): рисует радар «роза ветров» на /feed;
;; сам рендерер — локальный статик /js/radar.js (resources/public/js)
(def chart-js-src "https://cdn.jsdelivr.net/npm/chart.js@4.5.1/dist/chart.umd.js")
(def tailwind-src "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4")
(def daisyui-href "https://cdn.jsdelivr.net/npm/daisyui@5.7.15/daisyui.css")

;; PWA (change add-pwa-push): тема должна совпадать с manifest.webmanifest
(def theme-color "#5b5bea")

(defn html-attrs
  "Атрибуты <html>: язык и тема. Для :system атрибут data-theme не ставится —
   его установит inline-скрипт из head по prefers-color-scheme."
  [{:keys [theme]}]
  (merge {:lang (name i18n/*locale*)}
         (when (contains? #{:light :dark} theme)
           {:data-theme (name theme)})))

(defn head
  "Сформировать HTML-шапку с CDN-ресурсами, заголовком страницы, темой и CSRF
   meta-тегом. theme: :system | :light | :dark (из wrap-theme). При :system
   inline-скрипт ставит data-theme до первой отрисовки — сервер не знает тему ОС,
   а hyperscript исполняется после рендера и дал бы вспышку неправильной темы.
   csrf-token: токен антифоржери (nil если отсутствует)."
  [title csrf-token theme]
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1, viewport-fit=cover"}]
   (when (= theme :system)
     ;; raw обязателен: hiccup эскейпит кавычки в тексте <script>, а raw-text
     ;; элементы браузер не декодирует —escaped JS не исполнился бы
     (raw (str "<script>document.documentElement.dataset.theme=matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'</script>")))
   [:title title]
   (when csrf-token
     [:meta {:name "csrf-token" :content csrf-token}])
   ;; Шрифт Inter: подключаем <link>-ом, а не @import — Tailwind-CDN не
   ;; обрабатывает @import в <style>; preconnect ускоряет соединение
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "anonymous"}]
   [:link {:rel "stylesheet"
           :href "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"}]
   [:link {:rel "stylesheet" :href daisyui-href}]
   [:script {:src tailwind-src}]
   [:script {:src htmx-src}]
   [:script {:src json-enc-src}]
   [:script {:src hyperscript-src}]
   ;; defer сохраняет порядок: radar.js видит window.Chart
   [:script {:defer true :src chart-js-src}]
   [:script {:defer true :src "/js/radar.js"}]
   ;; push.js регистрирует service worker на каждой странице (installability)
   [:script {:defer true :src "/js/push.js"}]
   ;; PWA: манифест + цвет статус-бара Android; VAPID-ключ приезжает в meta
   ;; (публичный по определению) только когда ключи настроены на сервере
   [:link {:rel "manifest" :href "/manifest.webmanifest"}]
   [:meta {:name "theme-color" :content theme-color}]
   (when (env/env "GOODMOOD_VAPID_PUBLIC_KEY")
     [:meta {:name "vapid-public-key"
             :content (env/env "GOODMOOD_VAPID_PUBLIC_KEY")}])
   ;; Палитры тем: скоупленный plain CSS вместо @theme (Tailwind-CDN не
   ;; обрабатывает @theme без type=\"text/tailwindcss\", а переменные на :root
   ;; перекрываются темой на html). Тёмная — усреднение трёх макетов design/;
   ;; светлая — прежняя тёплая. Оттенки /60…/85 выводятся из базового
   ;; цвета через color-mix. Селекторы без кавычек: hiccup эскейпит \", а
   ;; raw-text <style> не декодирует сущности.
   [:style "
    /* Базовый шрифт: Inter для body, вне data-theme-блоков — общий для
       обеих тем; имя без кавычек, чтобы hiccup его не эскейпил */
    body {
      font-family: Inter, system-ui, sans-serif;
    }
    [data-theme=dark] {
      /* Тёмная палитра: усреднение макетов design/ (daisyUI-токены) */
      --color-base-100: #0c0f17;
      --color-base-200: #151827;
      --color-base-300: #1c2032;
      --color-base-content: #e6e8f0;
      --color-primary: #5b5bea;
      --color-primary-content: #ffffff;
      --color-secondary: #7c5ce0;
      --color-neutral: #1c2032;
      --color-success: #3fbf5f;
      --color-warning: #e8b33a;
      --color-error: #e05545;
      --color-info: #3b82f6;
    }
    [data-theme=light] {
      --color-base-100: #faf8f4;
      --color-base-200: #f1ede5;
      --color-base-300: #e6e0d5;
      --color-base-content: #4a4238;
      /* Явные hex (не oklch от daisyUI): radar.js дописывает hex-альфу
         к --color-primary/--color-secondary для градиента радара */
      --color-primary: #5b5bea;
      --color-secondary: #7c5ce0;
    }
    /* GM-токены: общие для обеих тем, поэтому вне data-theme-блоков */
    :root {
      /* State-цвета: пара фон+текст для state-бейджей, не маппится на
         daisyUI-семантику (success/error тянут свои контентные цвета) */
      --gm-state-danger-bg: #4a1f22;
      --gm-state-danger-text: #ef8a86;
      --gm-state-ok-bg: #1d3d28;
      --gm-state-ok-text: #7fd493;
      /* Акцентные цвета метрик: чипы энергии/тревоги/фокуса на главной */
      --gm-metric-energy: #f0c22e;
      --gm-metric-anxiety: #e05545;
      --gm-metric-focus: #3b82f6;
    }
    /* State-бейджи: подложка+текст из переменных, padding как в макете
       lenta (px-3 py-1.5 = 12px/6px), радиус 12px */
    .gm-badge-danger {
      background: var(--gm-state-danger-bg);
      color: var(--gm-state-danger-text);
      border-radius: 12px;
      padding: 6px 12px;
    }
    .gm-badge-ok {
      background: var(--gm-state-ok-bg);
      color: var(--gm-state-ok-text);
      border-radius: 12px;
      padding: 6px 12px;
    }
    /* Чип метрики: поверхность из токенов (в тёмной ~#1c2032, светлой —
       тёплый беж), padding = p-2.5 из макета (10px), радиус 12px */
    .gm-chip {
      background: var(--color-base-300);
      border: 1px solid color-mix(in oklab, var(--color-base-content) 10%, transparent);
      border-radius: 12px;
      padding: 10px;
    }
    /* Градиентный хелпер для точечных акцентов: period pill, save-кнопки */
    .gm-gradient {
      background: linear-gradient(135deg, #6366f1, #7c5ce0);
    }
    /* Левая акцентная полоса карточек ленты (совет, сводка): полоска 3.5px,
       absolute внутри relative-карточки; цвета из токенов — работает в обеих темах */
    .gm-accent-stripe {
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 3.5px;
      background: linear-gradient(to bottom, var(--color-primary), var(--color-secondary));
    }
    /* Мягкое свечение акцентных элементов (FAB, pill): цвет из токенов,
       на светлой теме становится деликатнее автоматически */
    .gm-glow {
      box-shadow: 0 0 24px color-mix(in oklab, var(--color-secondary) 45%, transparent),
                  0 0 10px color-mix(in oklab, var(--color-primary) 30%, transparent);
    }
    /* Glow-слайдер: нативный range, трек 8px, белый тумб с двойным свечением */
    .gm-range { -webkit-appearance: none; appearance: none; height: 8px; border-radius: 9999px; background: var(--color-base-300); width: 100%; }
    .gm-range::-webkit-slider-thumb { -webkit-appearance: none; appearance: none; width: 20px; height: 20px; border-radius: 50%; background: #ffffff; box-shadow: 0 0 15px rgba(255,255,255,0.8), 0 0 25px rgba(94,92,230,0.8); cursor: pointer; }
    .gm-range::-moz-range-thumb { width: 20px; height: 20px; border: none; border-radius: 50%; background: #ffffff; box-shadow: 0 0 15px rgba(255,255,255,0.8), 0 0 25px rgba(94,92,230,0.8); cursor: pointer; }
    /* Активный сегмент segmented control (check-in): hyperscript тогглит
       только класс tab-active, плашка рисуется здесь */
    .gm-segment .tab-active {
      background: var(--color-primary);
      color: var(--color-primary-content);
      border-radius: 12px;
      font-weight: 500;
    }
    /* Паттерн инпута форм (insights): единый стиль поля. Вынесен в CSS,
       а не копируемым классом: browser-сборка Tailwind не генерирует
       border-base-content/15 и focus:border-primary (проверено в
       Chromium), а применений уже больше трёх (rule of three) */
    .gm-input {
      width: 100%;
      background: var(--color-base-300);
      border: 1px solid color-mix(in oklab, var(--color-base-content) 20%, transparent);
      border-radius: 12px;
      padding: 12px 16px;
      /* 16px+ — чтобы iOS Safari не зумил поле при фокусе */
      font-size: 16px;
      color: var(--color-base-content);
      transition: border-color 0.15s ease;
    }
    .gm-input::placeholder {
      color: color-mix(in oklab, var(--color-base-content) 40%, transparent);
    }
    .gm-input:focus {
      outline: none;
      border-color: var(--color-primary);
    }
    /* Highlight утверждения о себе: акцентная рамка + мягкое свечение */
    .gm-input-highlight {
      border-color: var(--color-primary);
      box-shadow: 0 0 20px rgba(99, 102, 241, 0.15), 0 0 40px rgba(99, 102, 241, 0.05);
    }
    .gm-input-highlight:focus {
      border-color: var(--color-primary);
    }
   "]])

(defn layout
  "Отрендерить полную HTML-страницу с навигацией и содержимым.
   opts: map с ключом :title, опциональными :active (id пункта меню) и
   :request (ring-запрос, используется для identity/session/csrf/locale)
   nav-items: вектор пунктов навигации
   content: hiccup-контент для рендера в main"
  [{:keys [title active request]} nav-items content]
  (let [identity (:identity request)
        csrf-token (:anti-forgery-token request)
        theme (or (:theme request) :system)]
    [:html (html-attrs request)
     (head title csrf-token theme)
     [:body {:hx-boost "true"
             ;; Переменная высоты мобильной панели: её используют паддинг
             ;; контента и позиция FAB ассистента (см. navigation/assistant)
             :style {"--gm-nav-h" "88px"}
             :class "bg-base-100 min-h-dvh"}
      [:div {:class "hidden md:flex fixed left-0 top-0 h-screen w-64 flex-col"}
       (navigation/navigation :desktop nav-items {:active active})
       (when identity
         (user-menu/user-menu identity request))]
      [:div {:class (str "md:hidden fixed bottom-0 inset-x-0 z-50 bg-base-100 "
                         "border-t border-base-200 pb-[env(safe-area-inset-bottom)]")}
       (navigation/navigation :mobile nav-items {:active active})]
      [:main {:class "md:pl-64"}
       ;; PageShell: единая content-колонка — единственный источник ширины
       ;; и внешних отступов страницы; страницы рендерятся без своих обёрток
       [:div {:class (str "mx-auto w-full max-w-lg px-4 pt-4 "
                          "pb-[calc(env(safe-area-inset-bottom)+var(--gm-nav-h)+0.75rem)]")}
        content]]
      ;; Глобальный ассистент: FAB + bottom-sheet модалка (только для
      ;; залогиненных — на auth-страницах ассистента быть не должно)
      (when identity
        [:div {:id "assistant-root"}
         (assistant/fab)
         (assistant/modal)])]]))