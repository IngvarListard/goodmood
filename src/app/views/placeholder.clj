(ns app.views.placeholder
  (:require [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]))

(defn active-id
  "Return the nav item id whose :route matches the request path, or nil."
  [path]
  (some (fn [{:keys [id route]}]
          (when (= route path) id))
        navigation/nav-items))

(defn page
  "Render a placeholder page for a navigation route.
   opts: map with :title-key key (i18n key)
   request: ring request (uses :uri to derive the active nav item)"
  [{:keys [title-key]} request]
  (let [title (i18n/t title-key)
        content [:div {:class "max-w-2xl mx-auto p-4"}
                 [:h1 {:class "text-2xl font-bold mb-1"} title]
                 [:p {:class "text-sm opacity-70"} (i18n/t :pages/under-development)]]]
    (layout/layout {:title title
                    :active (active-id (:uri request))
                    :request request}
                   navigation/nav-items
                   content)))