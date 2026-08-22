(ns app.views.medications
  (:require [app.db.medications :as db]
            [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]
            [clojure.string :as str]))

(defn- today []
  (str (java.time.LocalDate/now)))

(defn- dose-str
  "Отформатировать дозу: 600.0 → \"600\", 300.5 → \"300.5\"."
  [dose]
  (if (== dose (long dose))
    (str (long dose))
    (str dose)))

(defn- schedule-str
  "Отобразить расписание медикамента (JSON из БД → \"08:00, 20:00\")."
  [schedule]
  (str/join ", " (db/parse-schedule schedule)))

(defn- slot-id
  [med-id scheduled-time]
  (str "slot-" med-id "-" (str/replace scheduled-time ":" "-")))

(defn intake-slot
  "Отрендерить карточку-слот приёма медикамента (форма, заменяемая по outerHTML).
   Не отмечен: кнопки «Принял»/«Пропустил»; отмечен: бейдж статуса + «Отменить»."
  [csrf-token {:keys [medication scheduled-time log]}]
  (let [med-id (:id medication)
        logged? (some? log)
        status (:status log)]
    [:form {:id (slot-id med-id scheduled-time)
            :hx-post (str "/medications/" med-id "/log")
            :hx-ext "json-enc"
            :hx-target (str "#" (slot-id med-id scheduled-time))
            :hx-swap "outerHTML"
            :class "card bg-base-200 p-3"}
     [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
     [:input {:type "hidden" :name "medication_id" :value med-id}]
     [:input {:type "hidden" :name "scheduled_time" :value scheduled-time}]
     [:input {:type "hidden" :name "log_date" :value (today)}]
     [:div {:class "flex items-center justify-between gap-3"}
      [:div {:class "flex items-center gap-3"}
       [:div
        [:p {:class "font-medium text-base"} (:name medication)]
        [:p {:class "text-sm opacity-60"}
         (str (dose-str (:dose medication)) " " (:dose-unit medication)
              " · " scheduled-time)]]
       (when logged?
         [:span {:class (str "badge badge-sm "
                             (if (= "taken" status) "badge-success" "badge-warning"))}
          (i18n/t (if (= "taken" status)
                    :medications/taken
                    :medications/skipped))])]
      (if logged?
        [:button {:type "submit"
                  :name "status" :value "pending"
                  :class "btn btn-ghost btn-sm h-11 min-h-11 px-3 text-sm opacity-50"}
         (i18n/t :medications/cancel-log)]
        [:div {:class "flex items-center gap-2"}
         [:button {:type "submit"
                   :name "status" :value "taken"
                   :class "btn btn-success btn-sm h-11 min-h-11 px-4"}
          (i18n/t :medications/taken)]
         [:button {:type "submit"
                   :name "status" :value "skipped"
                   :class "btn btn-warning btn-sm h-11 min-h-11 px-4"}
          (i18n/t :medications/skipped)]])]]))

(defn intake-widget
  "Отрендерить виджет приёма на сегодня: заголовок и слоты всех активных медикаментов."
  [csrf-token slots]
  [:div {:id "intake-widget" :class "space-y-3"}
   [:h2 {:class "text-lg font-semibold mb-2"} (i18n/t :medications/today)]
   (map #(intake-slot csrf-token %) slots)])

(defn med-card
  "Отрендерить карточку активного медикамента: name, dose+unit, расписание,
   sensitive-бейдж, кнопки «Редактировать» и «Деактивировать»."
  [csrf-token {:keys [id name dose dose-unit schedule sensitive]}]
  [:div {:class "card bg-base-200 p-4" :id (str "med-" id)}
   [:div {:class "flex items-start justify-between gap-2"}
    [:div
     [:h3 {:class "text-lg font-semibold"} name]
     [:p {:class "text-sm opacity-70"}
      (str (dose-str dose) " " dose-unit " · " (schedule-str schedule))]]
    [:div {:class "flex items-center gap-2"}
     (when (= 1 sensitive)
       [:span {:class "badge badge-neutral badge-sm"}
        (i18n/t :medications/sensitive)])
     [:button {:type "button"
               :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
               :hx-get (str "/medications/" id "/edit")
               :hx-target "#med-modal-box"
               :_ "on htmx:afterRequest call #med-modal.showModal()"}
      (i18n/t :medications/edit)]]]
   [:div {:class "mt-3 pt-3 border-t border-base-300"}
[:button {:type "button"
               :class "btn btn-ghost btn-sm h-11 min-h-11 text-sm opacity-60"
               :hx-post (str "/medications/" id "/deactivate")
               :hx-headers (str "{\"X-CSRF-Token\": \"" csrf-token "\"}")
               :hx-target "#med-content"
               :hx-swap "outerHTML"
               :hx-confirm (i18n/t :medications/deactivate-confirm {:name name})}
      (i18n/t :medications/deactivate)]]])

(defn inactive-card
  "Отрендерить карточку неактивного медикамента с кнопкой «Активировать»."
  [csrf-token {:keys [id name dose dose-unit schedule]}]
  [:div {:class "card bg-base-300/50 p-4" :id (str "med-" id)}
   [:div {:class "flex items-start justify-between gap-2"}
    [:div
     [:h3 {:class "text-base font-medium opacity-70"} name]
     [:p {:class "text-sm opacity-50"}
      (str (dose-str dose) " " dose-unit " · " (schedule-str schedule))]]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
              :hx-post (str "/medications/" id "/activate")
              :hx-headers (str "{\"X-CSRF-Token\": \"" csrf-token "\"}")
              :hx-target "#med-content"
              :hx-swap "outerHTML"}
     (i18n/t :medications/activate)]]])

(defn inactive-section
  "Отрендерить collapse со списком неактивных медикаментов."
  [csrf-token meds]
  [:div {:class "collapse collapse-arrow bg-base-200/50 mt-6 rounded-lg"}
   [:input {:type "checkbox"}]
   [:div {:class "collapse-title text-sm font-medium min-h-0 py-3"}
    (str (i18n/t :medications/inactive) " (" (count meds) ")")]
   [:div {:class "collapse-content space-y-3"}
    (map #(inactive-card csrf-token %) meds)]])

(defn empty-state
  "Онбординг-состояние для пустого реестра медикаментов."
  []
  [:div {:class "text-center py-12"}
   [:p {:class "text-lg opacity-60 mb-2"} (i18n/t :medications/empty-title)]
   [:p {:class "text-sm opacity-50 mb-6"} (i18n/t :medications/empty-desc)]
   [:button {:type "button"
             :class "btn btn-primary h-12 px-6"
             :hx-get "/medications/new"
             :hx-target "#med-modal-box"
             :_ "on htmx:afterRequest call #med-modal.showModal()"}
    (i18n/t :medications/empty-add)]])

(defn med-list
  "Отрендерить реестр активных медикаментов (или empty-state)."
  [csrf-token meds]
  (if (seq meds)
    [:div {:id "med-list" :class "space-y-3"}
     (map #(med-card csrf-token %) meds)]
    (empty-state)))

(defn medication-form
  "Отрендерить содержимое модалки формы медикамента (новая или редактирование).
   med: существующий медикамент или nil для новой записи."
  [csrf-token med]
  (let [creating? (nil? med)
        action (if creating? "/medications" (str "/medications/" (:id med)))]
    [:div
     [:h3 {:class "text-lg font-semibold mb-4"}
      (i18n/t (if creating? :medications/new :medications/edit))]
     [:form {:hx-post action
             :hx-ext "json-enc"
             :hx-target "#med-content"
             :hx-swap "outerHTML"
             :_ "on htmx:afterRequest
                   if event.detail.successful
                     call #med-modal.close()
                   end"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
      [:div {:class "form-control mb-4"}
       [:label {:class "label"}
        [:span {:class "label-text text-base font-medium"} (i18n/t :medications/name)]]
       [:input {:type "text" :name "name" :required true
                :placeholder "Препарат А"
                :value (:name med)
                :class "input input-bordered w-full h-12"}]]
      [:div {:class "flex gap-2 mb-4"}
       [:div {:class "form-control flex-1"}
        [:label {:class "label"}
         [:span {:class "label-text text-base font-medium"} (i18n/t :medications/dose)]]
        [:input {:type "number" :name "dose" :required true
                 :step "0.5" :min "0"
                 :placeholder "600"
                 :value (when (:dose med) (dose-str (:dose med)))
                 :class "input input-bordered w-full h-12"}]]
       [:div {:class "form-control w-28"}
        [:label {:class "label"}
         [:span {:class "label-text text-base font-medium"} (i18n/t :medications/dose-unit)]]
        [:select {:name "dose_unit" :class "select select-bordered w-full h-12"}
         (for [unit ["мг" "мл" "таб" "кап"]]
           [:option {:value unit
                     :selected (= unit (:dose-unit med))}
            unit])]]]
      [:div {:class "form-control mb-4"}
       [:label {:class "label"}
        [:span {:class "label-text text-base font-medium"} (i18n/t :medications/schedule)]]
       [:input {:type "text" :name "schedule"
                :placeholder "08:00, 20:00"
                :value (when (:schedule med) (schedule-str (:schedule med)))
                :class "input input-bordered w-full h-12"}]
       [:label {:class "label"}
        [:span {:class "label-text-alt text-xs opacity-50"}
         (i18n/t :medications/schedule-hint)]]]
      [:div {:class "form-control mb-4 flex-row items-center justify-between"}
       [:span {:class "text-base font-medium"} (i18n/t :medications/sensitive-label)]
       [:input {:type "checkbox" :name "sensitive" :value "1"
                :checked (= 1 (:sensitive med))
                :class "toggle toggle-neutral"}]]
      [:div {:class "form-control mb-4"}
       [:label {:class "label"}
        [:span {:class "label-text text-base font-medium"} (i18n/t :medications/notes)]]
       [:textarea {:name "notes" :rows "2" :maxlength "500"
                   :placeholder "..."
                   :class "textarea textarea-bordered w-full"}
        (:notes med)]]
      [:div {:class "modal-action mt-6"}
       [:button {:type "submit"
                 :class "btn btn-primary h-12 px-6"}
        (i18n/t :medications/save)]
       [:button {:type "button"
                 :class "btn btn-ghost h-12"
                 :_ "on click call #med-modal.close()"}
        (i18n/t :medications/cancel)]]]]))

(defn page-content
  "Отрендерить содержимое #med-content: виджет приёма, реестр активных и
   collapse неактивных. Заменяется целиком по outerHTML при изменениях."
  [csrf-token meds slots]
  [:div {:id "med-content"}
   (when (seq slots)
     (intake-widget csrf-token slots))
   [:div {:id "med-error" :class "mb-3"}]
   (med-list csrf-token (filter #(= 1 (:active %)) meds))
   (when (seq (remove #(= 1 (:active %)) meds))
     (inactive-section csrf-token (remove #(= 1 (:active %)) meds)))
   [:dialog {:id "med-modal" :class "modal"}
    [:div {:class "modal-box" :id "med-modal-box"}]
    [:form {:method "dialog" :class "modal-backdrop"}
     [:button "close"]]]])

(defn page
  "Отрендерить полную страницу реестра медикаментов."
  [request meds slots]
  (let [csrf-token (:anti-forgery-token request)
        content [:div {:class "max-w-2xl mx-auto p-4"}
                 [:div {:class "flex items-center justify-between mb-1"}
                  [:h1 {:class "text-2xl font-bold"} (i18n/t :medications/title)]
                  [:button {:type "button"
                            :class "btn btn-primary btn-sm h-11 min-h-11 px-4"
                            :hx-get "/medications/new"
                            :hx-target "#med-modal-box"
                            :_ "on htmx:afterRequest call #med-modal.showModal()"}
                   (i18n/t :medications/add)]]
                 [:p {:class "text-sm opacity-70 mb-6"}
                  (i18n/t :medications/description)]
                 (page-content csrf-token meds slots)]]
    (layout/layout {:title (i18n/t :medications/title)
                    :active :medications
                    :request request}
                   navigation/nav-items
                   content)))