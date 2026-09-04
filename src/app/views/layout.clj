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
   [:link {:rel "stylesheet" :href daisyui-href}]
   [:script {:src tailwind-src}]
   [:script {:src htmx-src}]
   [:script {:src json-enc-src}]
   [:script {:src hyperscript-src}]
   ;; Тёплая палитра: скоупленный plain CSS вместо @theme (Tailwind-CDN не
   ;; обрабатывает @theme без type=\"text/tailwindcss\", а переменные на :root
   ;; перекрываются темой на html). Оттенки /60…/85 выводятся из базового
   ;; цвета через color-mix. Селекторы без кавычек: hiccup эскейпит \", а
   ;; raw-text <style> не декодирует сущности.
   [:style "
    [data-theme=dark] {
      --color-base-content: #e8e5df;
    }
    [data-theme=light] {
      --color-base-100: #faf8f4;
      --color-base-200: #f1ede5;
      --color-base-300: #e6e0d5;
      --color-base-content: #4a4238;
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