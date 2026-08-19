(ns app.views.layout
  (:require [app.i18n :as i18n]
            [app.views.navigation :as navigation]
            [app.views.user-menu :as user-menu]))

(def htmx-src "https://unpkg.com/htmx.org@2.0.10/dist/htmx.min.js")
(def json-enc-src "https://unpkg.com/htmx.org@2.0.10/dist/ext/json-enc.js")
(def hyperscript-src "https://unpkg.com/hyperscript.org@0.9.93/dist/_hyperscript.min.js")
(def tailwind-src "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4")
(def daisyui-href "https://cdn.jsdelivr.net/npm/daisyui@5.7.15/daisyui.css")

(defn head
  "Сформировать HTML-шапку с CDN-ресурсами, заголовком страницы и CSRF meta-тегом.
   csrf-token: токен антифоржери (nil если отсутствует)."
  [title csrf-token]
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:title title]
   (when csrf-token
     [:meta {:name "csrf-token" :content csrf-token}])
   [:link {:rel "stylesheet" :href daisyui-href}]
   [:script {:src tailwind-src}]
   [:script {:src htmx-src}]
   [:script {:src json-enc-src}]
   [:script {:src hyperscript-src}]])

(defn layout
  "Отрендерить полную HTML-страницу с навигацией и содержимым.
   opts: map с ключом :title, опциональными :active (id пункта меню) и
   :request (ring-запрос, используется для identity/session/csrf/locale)
   nav-items: вектор пунктов навигации
   content: hiccup-контент для рендера в main"
  [{:keys [title active request]} nav-items content]
  (let [identity (:identity request)
        csrf-token (:anti-forgery-token request)]
    [:html {:lang (name i18n/*locale*)}
     (head title csrf-token)
     [:body {:data-theme "dark"
             :hx-boost "true"
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