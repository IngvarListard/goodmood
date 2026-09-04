(ns app.views.layout
  (:require [app.i18n :as i18n]
            [app.views.navigation :as navigation]
            [app.views.user-menu :as user-menu]
            [hiccup2.core :refer [raw]]))

(def htmx-src "https://unpkg.com/htmx.org@2.0.10/dist/htmx.min.js")
(def json-enc-src "https://unpkg.com/htmx.org@2.0.10/dist/ext/json-enc.js")
(def hyperscript-src "https://unpkg.com/hyperscript.org@0.9.93/dist/_hyperscript.min.js")
(def tailwind-src "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4")
(def daisyui-href "https://cdn.jsdelivr.net/npm/daisyui@5.7.15/daisyui.css")

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
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
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
    /* Чип метрики: подложка и бордер из макета lenta, padding = p-2.5
       из макета (10px), радиус 12px */
    .gm-chip {
      background: #191d2c;
      border: 1px solid #282d42;
      border-radius: 12px;
      padding: 10px;
    }
    /* Градиентный хелпер для точечных акцентов: period pill, save-кнопки */
    .gm-gradient {
      background: linear-gradient(135deg, #6366f1, #7c5ce0);
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
             :class "bg-base-100 min-h-screen"}
      [:div {:class "hidden md:flex fixed left-0 top-0 h-screen w-64 flex-col"}
       (navigation/navigation :desktop nav-items {:active active})
       (when identity
         (user-menu/user-menu identity request))]
      [:div {:class (str "md:hidden fixed bottom-0 inset-x-0 z-50 bg-base-100 "
                         "border-t border-base-200 pb-[env(safe-area-inset-bottom)]")}
       (navigation/navigation :mobile nav-items {:active active})]
      [:main {:class "md:pl-64 pb-16"}
       content]]]))