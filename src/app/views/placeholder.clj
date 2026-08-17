(ns app.views.placeholder
  (:require [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]))

(defn active-id
  "Вернуть id пункта навигации, чей :route совпадает с путём запроса, или nil."
  [path]
  (some (fn [{:keys [id route]}]
          (when (= route path) id))
        navigation/nav-items))

(defn page
  "Отрендерить страницу-заглушку для навигационного маршрута.
   opts: map с ключом :title-key (i18n-ключ)
   request: ring-запрос (:uri используется для определения активного пункта меню)"
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