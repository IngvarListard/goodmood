(ns app.views.layout
  (:require [app.views.navigation :as navigation]))

(def htmx-src "https://unpkg.com/htmx.org@2.0.10/dist/htmx.min.js")
(def json-enc-src "https://unpkg.com/htmx.org@2.0.10/dist/ext/json-enc.js")
(def hyperscript-src "https://unpkg.com/hyperscript.org@0.9.93/dist/_hyperscript.min.js")
(def tailwind-src "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4")
(def daisyui-href "https://cdn.jsdelivr.net/npm/daisyui@5.7.15/daisyui.css")

(defn layout
  "Render a complete HTML page with navigation and content.
   opts: map with :title key and optional :active (nav item id)
   nav-items: vector of navigation items
   content: hiccup content to render in main"
  [{:keys [title active]} nav-items content]
  [:html {:lang "ru"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title title]
    [:link {:rel "stylesheet" :href daisyui-href}]
    [:script {:src tailwind-src}]
    [:script {:src htmx-src}]
    [:script {:src json-enc-src}]
    [:script {:src hyperscript-src}]]
   [:body {:data-theme "light"
           :hx-boost "true"
           :class "bg-base-100 min-h-screen"}
    [:div {:class "hidden md:flex fixed left-0 top-0 h-screen w-64"}
     (navigation/navigation :desktop nav-items {:active active})]
    [:div {:class (str "md:hidden fixed bottom-0 inset-x-0 z-50 bg-base-100 "
                       "border-t border-base-200 pb-[env(safe-area-inset-bottom)]")}
     (navigation/navigation :mobile nav-items {:active active})]
    [:main {:class "md:pl-64 pb-16"}
     content]]])