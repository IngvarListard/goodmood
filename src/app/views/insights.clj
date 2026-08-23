(ns app.views.insights
  (:require [app.i18n :as i18n]
            [app.views.layout :as layout]
            [app.views.navigation :as navigation]
            [clojure.string :as str]))

(def categories
  "Список категорий для select и фильтра-табов."
  ["productivity" "coping" "identity" "general"])

(def state-labels
  "Список state_label для select (без :state/ префикса — raw ключи)."
  ["mixed" "anxiety" "elevated" "low" "balanced" "neutral"])

(defn- category-label
  [category]
  (i18n/t (keyword "insights" (str "category-" category))))

(defn- state-label-text
  [state-label]
  (if (str/blank? state-label)
    (i18n/t :insights/state-choose)
    (i18n/t (keyword "state" state-label))))

(defn- format-date
  "Отформатировать created_at (SQLite datetime) в «14 авг» / «Aug 14»."
  [created-at]
  (when created-at
    (try
      (let [date (java.time.LocalDateTime/parse created-at)
            today (java.time.LocalDate/now)
            date-only (.toLocalDate date)]
        (cond
          (= date-only today) (i18n/t :feed/today)
          (= date-only (.minusDays today 1)) (i18n/t :feed/yesterday)
          :else (i18n/t :insights/created-at
                        {:date (str (.getDayOfMonth date) " "
                                    (i18n/t (keyword "feed" (str "month-" (.getMonthValue date)))))})))
      (catch Exception _ created-at))))

;; ──────────────────────────────────────────────────────────────
;; 1. Страница /insights — список
;; ──────────────────────────────────────────────────────────────

(defn- category-tab
  [active-category category value label]
  (let [active? (= active-category (or category "all"))]
    [:a {:role "tab"
         :class (str "tab" (when active? " tab-active"))
         :hx-get (if category (str "/insights?category=" category) "/insights")
         :hx-target "#insights-list"
         :hx-select "#insights-list"
         :_ "on click remove .tab-active from .tab then add .tab-active to me"}
     label]))

(defn- insight-list-card
  "Карточка инсайта для списка /insights + её modal удаления.
   Возвращает seq из двух элементов: card и dialog."
  [csrf-token insight]
  (let [id (:id insight)
        advice (:advice-to-self insight)
        first-advice (first advice)
        extra-count (max 0 (dec (count advice)))
        identity-text (:identity insight)
        card
        [:div {:class "card bg-base-200 shadow-sm" :id (str "insight-" id)}
         [:div {:class "card-body p-4"}
          [:div {:class "flex items-start justify-between gap-2 mb-2"}
           [:div {:class "flex items-center gap-2 flex-wrap"}
            [:span {:class "badge badge-secondary badge-sm"} (category-label (:category insight))]
            (when (:state-label insight)
              [:span {:class "badge badge-ghost badge-sm"} (state-label-text (:state-label insight))])
            [:span {:class "text-xs opacity-50 ml-auto tabular-nums"} (format-date (:created-at insight))]]
           [:div {:class "flex items-center gap-1"}
            [:a {:href (str "/insights/" id)
                 :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"}
             (i18n/t :insights/edit)]
            [:button {:type "button"
                      :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                      :aria-label (i18n/t :insights/delete-insight)
                      :_ (str "on click toggle .modal-open on #del-insight-" id)}
             [:svg {:xmlns "http://www.w3.org/2000/svg"
                    :width 18 :height 18 :viewBox "0 0 24 24"
                    :fill "none" :stroke "currentColor"
                    :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
              [:path {:d "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"}]]]]]
          [:p {:class "text-sm mb-2 line-clamp-2"} (:context insight)]
          [:div {:id (str "advice-preview-" id)}
           (when first-advice
             [:p {:class "text-sm opacity-70 line-clamp-1"} first-advice])
           (when (pos? extra-count)
             [:button {:type "button" :class "btn btn-ghost btn-xs mt-1"
                       :hx-get (str "/insights/" id "/advice-items")
                       :hx-target (str "#advice-preview-" id)
                       :hx-swap "innerHTML"}
              (i18n/t :insights/more-advice {:n extra-count})])]
          (when identity-text
            [:p {:class "text-sm italic opacity-70 mt-2 line-clamp-1"} identity-text])]]
        dialog
        [:dialog {:id (str "del-insight-" id) :class "modal modal-bottom sm:modal-middle"}
         [:div {:class "modal-box"}
          [:h3 {:class "text-lg font-bold mb-2"} (i18n/t :insights/delete-confirm)]
          [:p {:class "text-sm opacity-70 mb-4"} (i18n/t :insights/delete-confirm-text)]
          [:div {:class "modal-action"}
           [:form {:method "dialog"}
            [:button {:class "btn btn-ghost h-11 min-h-11"} (i18n/t :insights/cancel)]]
           [:button {:class "btn btn-error h-11 min-h-11"
                     :hx-delete (str "/insights/" id)
                     :hx-headers (str "{\"X-CSRF-Token\": \"" csrf-token "\"}")
                     :_ (str "on htmx:afterRequest remove #insight-" id " then call #del-insight-" id ".close()")}
            (i18n/t :insights/delete-action)]]]
         [:form {:method "dialog" :class "modal-backdrop"}
          [:button "close"]]]]
    (list card dialog)))

(defn advice-items-fragment
  "Фрагмент «все советы» для раскрытия в карточке списка (htmx-get
   /insights/:id/advice-items). Заменяет #advice-preview-<id> по innerHTML."
  [insight]
  (let [id (:id insight)]
    [:div {:id (str "advice-preview-" id)}
     (if (seq (:advice-to-self insight))
       [:ul {:class "text-sm space-y-1 opacity-85 mt-1"}
        (for [item (:advice-to-self insight)]
          [:li [:span {:class "opacity-50 mr-1"} "•"] " " item])]
       [:p {:class "text-sm opacity-50"} ""])]))

(defn- insights-list
  "Список карточек инсайтов (без фильтра и заголовка) — для htmx-свалопа."
  [csrf-token insights]
  [:div {:id "insights-list" :class "space-y-3"}
   (if (seq insights)
     (map #(insight-list-card csrf-token %) insights)
     [:div {:class "text-center py-10"}
      [:p {:class "opacity-70 mb-1"} (i18n/t :insights/empty-category)]
      [:p {:class "text-sm opacity-50"} (i18n/t :insights/empty-category-hint)]])])

(defn- global-onboarding
  "Глобальный онбординг при отсутствии инсайтов."
  []
  [:div {:class "text-center py-16"}
   [:p {:class "text-lg mb-2"} (i18n/t :insights/onboarding-title)]
   [:p {:class "text-sm opacity-70 mb-6 max-w-sm mx-auto"}
    (i18n/t :insights/onboarding-desc)]
   [:a {:href "/insights/new" :class "btn btn-primary"}
    (i18n/t :insights/onboarding-create)]])

(defn list-page
  "Отрендерить страницу /insights со списком, фильтрами и онбордингом.
   request: ring-запрос; insights: вектор инсайтов; active-category: строка
   или nil (для \"все\"). Если insights пусто и нет фильтра — глобальный онбординг."
  [request insights active-category]
  (let [csrf-token (:anti-forgery-token request)
        has-insights? (seq insights)
        global-empty? (and (str/blank? active-category) (not has-insights?))]
    (layout/layout
     {:title (i18n/t :insights/title)
      :active :insights
      :request request}
     navigation/nav-items
     [:div {:class "max-w-2xl mx-auto p-4 pb-24"}
      [:div {:class "card bg-base-200 shadow-sm mb-4"}
       [:div {:class "card-body p-4"}
        [:div {:class "flex items-center justify-between mb-1"}
         [:h1 {:class "text-2xl font-bold"} (i18n/t :insights/title)]
         [:a {:href "/insights/new"
              :class "btn btn-primary btn-sm h-11 min-h-11 px-4"}
          (i18n/t :insights/new)]]
        [:p {:class "text-sm opacity-70"} (i18n/t :insights/subtitle)]]]
      (if global-empty?
        (global-onboarding)
        [:div {:class "card bg-base-200 shadow-sm mb-4"}
         [:div {:class "card-body p-4"}
          [:div {:class "tabs tabs-boxed" :role "tablist"}
           (category-tab active-category nil "all" (i18n/t :insights/category-all))
           (for [cat categories]
             (category-tab active-category cat cat (category-label cat)))]]
         (insights-list csrf-token insights)])])))

;; ──────────────────────────────────────────────────────────────
;; 2. Страница /insights/new — форма создания
;; ──────────────────────────────────────────────────────────────

(defn- state-option
  [selected state-label]
  [:option {:value state-label :selected (= state-label selected)}
   (state-label-text state-label)])

(defn- advice-textarea
  "Текстarea пункта advice (общее имя advice_to_self — json-enc +
   hyperscript собирает в JSON-массив на htmx:configRequest).
   Без required: пустой список советов валидируется на сервере
   (мягкий alert), а не нативным браузерным тултипом."
  [index value placeholder]
  [:textarea {:name "advice_to_self"
              :class "textarea textarea-bordered w-full h-20"
              :placeholder (or placeholder (i18n/t :insights/advice-placeholder))}
   (or value "")])

(defn- advice-item
  "Один пункт advice (textarea + кнопка удаления)."
  ([index value]
   [:div {:class "advice-item flex items-start gap-2"}
    (advice-textarea index value nil)])
  ([index value show-delete?]
   [:div {:class "advice-item flex items-start gap-2"}
    (advice-textarea index value nil)
    (when show-delete?
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
                :aria-label (i18n/t :insights/delete-advice)
                :_ "on click remove closest .advice-item"}
       [:svg {:xmlns "http://www.w3.org/2000/svg"
              :width 18 :height 18 :viewBox "0 0 24 24"
              :fill "none" :stroke "currentColor"
              :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M18 6L6 18M6 6l12 12"}]]])]))

(defn advice-item-fragment
  "Фрагмент нового пункта advice (ответ на htmx-get /insights/advice-item)."
  []
  (advice-item 1 nil))

(defn validation-error-fragment
  "Мягкий alert ошибки валидации."
  [message]
  [:div {:class "alert alert-warning shadow-sm"}
   [:svg {:xmlns "http://www.w3.org/2000/svg"
          :width 20 :height 20 :viewBox "0 0 24 24"
          :fill "none" :stroke "currentColor"
          :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
    [:circle {:cx 12 :cy 12 :r 10}]
    [:path {:d "M12 8v4M12 16h.01"}]]
   [:span {:class "text-sm"} (or message (i18n/t :insights/validation-error))]])

(defn new-page
  "Отрендерить форму создания инсайта.
   request: ring-запрос; params: map с предзаполненными значениями
   (:state-label, :entry-id, :context) из query params."
  [request {:keys [state-label entry-id context] :as params}]
  (let [csrf-token (:anti-forgery-token request)]
    (layout/layout
     {:title (i18n/t :insights/new-title)
      :active :insights
      :request request}
     navigation/nav-items
     [:div {:class "max-w-2xl mx-auto p-4 pb-24"}
      [:div {:class "card bg-base-200 shadow-sm mb-4"}
       [:div {:class "card-body p-4"}
        [:div {:class "flex items-center gap-3 mb-1"}
         [:a {:href "/insights"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
              :aria-label (i18n/t :insights/back)}
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 :width 20 :height 20 :viewBox "0 0 24 24"
                 :fill "none" :stroke "currentColor"
                 :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
           [:path {:d "M15 18l-6-6 6-6"}]]]
         [:h1 {:class "text-2xl font-bold"} (i18n/t :insights/new-title)]]
        [:p {:class "text-sm opacity-70"} (i18n/t :insights/subtitle)]]]

      [:div {:class "card bg-base-200 shadow-sm"}
       [:div {:class "card-body p-4"}
        [:div {:id "form-error" :class "mb-2"}]
        [:form {:id "insight-form"
                :hx-post "/insights"
                :hx-ext "json-enc"
                :hx-target "#form-error"
                :hx-swap "innerHTML"
                :_ "on htmx:configRequest(detail) set detail.parameters.advice_to_self to JSON.stringify(Array.from(detail.formData.getAll('advice_to_self')))"
                :class "space-y-4"}
         [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
         (when entry-id
           [:input {:type "hidden" :name "entry_id" :value (str entry-id)}])

         [:div {:class "form-control"}
          [:label {:class "label" :for "context"}
           [:span {:class "label-text"} (i18n/t :insights/context-label)]]
          [:textarea {:id "context" :name "context"
                      :class "textarea textarea-bordered w-full h-24"
                      :placeholder (i18n/t :insights/context-placeholder)
                      :required true}
           (or context "")]
          [:label {:class "label"}
           [:span {:class "label-text-alt opacity-60"}
            (i18n/t :insights/context-hint)]]]

         [:div {:class "form-control"}
          [:label {:class "label" :for "state_label"}
           [:span {:class "label-text"} (i18n/t :insights/state-label)]]
          [:select {:id "state_label" :name "state_label"
                    :class "select select-bordered w-full"
                    :required true}
           [:option {:value "" :disabled true :selected (str/blank? state-label)}
            (i18n/t :insights/state-choose)]
           (for [sl state-labels]
             (state-option state-label sl))]
          [:label {:class "label"}
           [:span {:class "label-text-alt opacity-60"}
            (i18n/t :insights/state-hint)]]]

         [:div {:class "form-control"}
          [:label {:class "label" :for "category"}
           [:span {:class "label-text"} (i18n/t :insights/category-label)]]
          [:select {:id "category" :name "category"
                    :class "select select-bordered w-full"
                    :required true}
           [:option {:value "" :disabled true :selected true}
            (i18n/t :insights/category-choose)]
           (for [cat categories]
             [:option {:value cat} (category-label cat)])]]

[:div {:class "form-control"}
           [:label {:class "label"}
            [:span {:class "label-text"} (i18n/t :insights/advice-label)]]
           [:p {:class "text-sm opacity-60 mb-2"} (i18n/t :insights/advice-hint)]
           [:div {:id "advice-list" :class "space-y-2"}
            (advice-item 0 nil)]
           [:button {:type "button"
                     :class "btn btn-ghost btn-sm h-11 min-h-11 mt-2"
                     :hx-get "/insights/advice-item"
                     :hx-target "#advice-list"
                     :hx-swap "beforeend"}
            [:svg {:xmlns "http://www.w3.org/2000/svg"
                   :width 20 :height 20 :viewBox "0 0 24 24"
                   :fill "none" :stroke "currentColor"
                   :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
             [:path {:d "M12 5v14M5 12h14"}]]
            (i18n/t :insights/add-advice)]]

         [:div {:class "form-control"}
          [:label {:class "label" :for "identity"}
           [:span {:class "label-text"}
            (i18n/t :insights/identity-label)
            [:span {:class "opacity-50 ml-1"} (i18n/t :insights/identity-optional)]]]
          [:textarea {:id "identity" :name "identity"
                      :class "textarea textarea-bordered w-full h-20"
                      :placeholder (i18n/t :insights/identity-placeholder)}
           ""]
          [:label {:class "label"}
           [:span {:class "label-text-alt opacity-60"}
            (i18n/t :insights/identity-hint)]]]

         [:div {:class "form-control mt-4"}
          [:button {:id "submit-btn"
                    :type "submit"
                    :class "btn btn-primary h-11 min-h-11 w-full"}
           (i18n/t :insights/save)]]]]]])))

;; ──────────────────────────────────────────────────────────────
;; 3. Страница /insights/:id — просмотр + in-place edit
;; ──────────────────────────────────────────────────────────────

(defn context-read-block
  "Read-блок context для in-place edit."
  [insight]
  [:p {:class "text-sm"} (:context insight)])

(defn context-edit-fragment
  "Edit-форма context (ответ на hx-get)."
  [csrf-token insight]
  [:form {:id "context-block"
          :hx-post (str "/insights/" (:id insight) "/context")
          :hx-ext "json-enc"
          :hx-target "this"
          :hx-swap "outerHTML"
          :class "space-y-2"}
   [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
   [:textarea {:name "context"
               :class "textarea textarea-bordered w-full h-24"}
    (:context insight)]
   [:div {:class "flex gap-2"}
    [:button {:type "submit" :class "btn btn-primary btn-sm h-11 min-h-11"}
     (i18n/t :insights/save)]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11"
              :hx-get (str "/insights/" (:id insight) "/context")
              :hx-target "#context-block"
              :hx-swap "outerHTML"}
     (i18n/t :insights/cancel)]]])

(defn advice-read-block
  "Read-блок advice (нумерованный список)."
  [insight]
  [:ol {:class "text-sm space-y-2 list-decimal list-inside"}
   (for [item (:advice-to-self insight)]
     [:li item])])

(defn advice-edit-fragment
  "Edit-форма advice (динамический список)."
  [csrf-token insight]
  [:form {:id "advice-block"
          :hx-post (str "/insights/" (:id insight) "/advice")
          :hx-ext "json-enc"
          :hx-target "this"
          :hx-swap "outerHTML"
          :_ "on htmx:configRequest(detail) set detail.parameters.advice_to_self to JSON.stringify(Array.from(detail.formData.getAll('advice_to_self')))"
          :class "space-y-2"}
   [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
   [:div {:class "space-y-2"}
    (map-indexed
     (fn [idx item]
       (advice-item idx item))
     (:advice-to-self insight))]
   [:button {:type "button"
             :class "btn btn-ghost btn-sm h-11 min-h-11"
             :hx-get "/insights/advice-item"
             :hx-target "#advice-block"
             :hx-swap "beforeend"}
    (i18n/t :insights/add-advice)]
   [:div {:class "flex gap-2 mt-2"}
    [:button {:type "submit"
              :class "btn btn-primary btn-sm h-11 min-h-11"}
     (i18n/t :insights/save)]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11"
              :hx-get (str "/insights/" (:id insight) "/advice")
              :hx-target "#advice-block"
              :hx-swap "outerHTML"}
     (i18n/t :insights/cancel)]]])

(defn identity-read-block
  "Read-блок identity (курсив, opacity 80%)."
  [insight]
  [:p {:class "text-sm italic opacity-80"} (:identity insight)])

(defn identity-edit-fragment
  "Edit-форма identity."
  [csrf-token insight]
  [:form {:id "identity-block"
          :hx-post (str "/insights/" (:id insight) "/identity")
          :hx-ext "json-enc"
          :hx-target "this"
          :hx-swap "outerHTML"
          :class "space-y-2"}
   [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
   [:textarea {:name "identity"
               :class "textarea textarea-bordered w-full h-20"
               :placeholder (i18n/t :insights/identity-placeholder)}
    (or (:identity insight) "")]
   [:div {:class "flex gap-2"}
    [:button {:type "submit" :class "btn btn-primary btn-sm h-11 min-h-11"}
     (i18n/t :insights/save)]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11"
              :hx-get (str "/insights/" (:id insight) "/identity")
              :hx-target "#identity-block"
              :hx-swap "outerHTML"}
     (i18n/t :insights/cancel)]]])

(defn- field-card
  "Карточка поля с заголовком и кнопкой «Править».
   label: i18n ключ; read-content: hiccup-вектор; edit-url: GET URL для edit."
  [label read-content edit-url]
  [:div {:class "card bg-base-200 shadow-sm mb-4"}
   [:div {:class "card-body p-4"}
    [:div {:class "flex items-center justify-between mb-2"}
     [:h2 {:class "text-sm font-medium opacity-60 uppercase tracking-wide"} label]
     [:button {:type "button"
               :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
               :hx-get edit-url
               :hx-target "#field-block"
               :hx-swap "outerHTML"}
      (i18n/t :insights/edit)]]
    [:div {:id "field-block"} read-content]]])

(defn show-page
  "Отрендерить страницу /insights/:id — полный инсайт с in-place edit."
  [request insight]
  (let [csrf-token (:anti-forgery-token request)
        id (:id insight)]
    (layout/layout
     {:title (i18n/t :insights/title)
      :active :insights
      :request request}
     navigation/nav-items
     [:div {:class "max-w-2xl mx-auto p-4 pb-24" :id "insight-page"}
      ;; Шапка: назад + бейджи
      [:div {:class "card bg-base-200 shadow-sm mb-4"}
       [:div {:class "card-body p-4"}
        [:div {:class "flex items-center gap-3 mb-2"}
         [:a {:href "/insights"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
              :aria-label (i18n/t :insights/back)}
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 :width 20 :height 20 :viewBox "0 0 24 24"
                 :fill "none" :stroke "currentColor"
                 :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
           [:path {:d "M15 18l-6-6 6-6"}]]]
         [:h1 {:class "text-2xl font-bold"} (i18n/t :insights/title)]]
        [:div {:class "flex items-center gap-2 flex-wrap"}
         [:span {:class "badge badge-secondary badge-sm"} (category-label (:category insight))]
         (when (:state-label insight)
           [:span {:class "badge badge-ghost badge-sm"} (state-label-text (:state-label insight))])]]]

      ;; Контекст
      [:div {:class "card bg-base-200 shadow-sm mb-4"}
       [:div {:class "card-body p-4"}
        [:div {:class "flex items-center justify-between mb-2"}
         [:h2 {:class "text-sm font-medium opacity-60 uppercase tracking-wide"}
          (i18n/t :insights/context-label)]
         [:button {:type "button"
                   :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                   :hx-get (str "/insights/" id "/edit-context")
                   :hx-target "#context-block"
                   :hx-swap "outerHTML"}
          (i18n/t :insights/edit)]]
        [:div {:id "context-block"} (context-read-block insight)]]]

      ;; Советы
      [:div {:class "card bg-base-200 shadow-sm mb-4"}
       [:div {:class "card-body p-4"}
        [:div {:class "flex items-center justify-between mb-2"}
         [:h2 {:class "text-sm font-medium opacity-60 uppercase tracking-wide"}
          (i18n/t :insights/advice-label)]
         [:button {:type "button"
                   :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                   :hx-get (str "/insights/" id "/edit-advice")
                   :hx-target "#advice-block"
                   :hx-swap "outerHTML"}
          (i18n/t :insights/edit)]]
        [:div {:id "advice-block"} (advice-read-block insight)]]]

      ;; Идентичность (опционально)
      (when (:identity insight)
        [:div {:class "card bg-base-200 shadow-sm mb-4"}
         [:div {:class "card-body p-4"}
          [:div {:class "flex items-center justify-between mb-2"}
           [:h2 {:class "text-sm font-medium opacity-60 uppercase tracking-wide"}
            (i18n/t :insights/identity-label)]
           [:button {:type "button"
                     :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"
                     :hx-get (str "/insights/" id "/edit-identity")
                     :hx-target "#identity-block"
                     :hx-swap "outerHTML"}
            (i18n/t :insights/edit)]]
          [:div {:id "identity-block"} (identity-read-block insight)]]])

      ;; Метаданные + действия
      [:div {:class "card bg-base-200 shadow-sm"}
       [:div {:class "card-body p-4"}
        [:div {:class "flex items-center justify-between flex-wrap gap-2"}
         [:span {:class "text-xs opacity-50 tabular-nums"}
          (format-date (:created-at insight))]
         [:button {:type "button"
                   :class "btn btn-ghost btn-sm h-11 min-h-11 px-3 text-error"
:aria-label (i18n/t :insights/delete-insight)
                   :_ (str "on click toggle .modal-open on #del-insight-" id)}
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 :width 18 :height 18 :viewBox "0 0 24 24"
                 :fill "none" :stroke "currentColor"
                 :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
           [:path {:d "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"}]]
          (i18n/t :insights/delete)]]]]

      ;; Modal удаления
      [:dialog {:id (str "del-insight-" id) :class "modal modal-bottom sm:modal-middle"}
       [:div {:class "modal-box"}
        [:h3 {:class "text-lg font-bold mb-2"} (i18n/t :insights/delete-confirm)]
        [:p {:class "text-sm opacity-70 mb-4"} (i18n/t :insights/delete-confirm-text)]
        [:div {:class "modal-action"}
         [:form {:method "dialog"}
          [:button {:class "btn btn-ghost h-11 min-h-11"} (i18n/t :insights/cancel)]]
         [:button {:class "btn btn-error h-11 min-h-11"
                   :hx-delete (str "/insights/" id)
                   :hx-headers (str "{\"X-CSRF-Token\": \"" csrf-token "\"}")
                   :_ (str "on htmx:afterRequest settle window.location.href = '/insights'")}
          (i18n/t :insights/delete-action)]]]
       [:form {:method "dialog" :class "modal-backdrop"}
        [:button "close"]]]])))

;; ──────────────────────────────────────────────────────────────
;; 4. Виджет инсайтов на /feed
;; ──────────────────────────────────────────────────────────────

(defn feed-widget
  "Виджет для /feed: заголовок + 1 компактная карточка или онбординг.
   state-label: текущий state_label (string, напр. \"anxiety\").
   insight: 1 релевантный инсайт или nil."
  [state-label insight]
  (when state-label
    [:section {:class "mb-4" :id "feed-insights"}
     [:h2 {:class "text-sm font-medium opacity-60 mb-2 uppercase tracking-wide"}
      (i18n/t :insights/widget-title)]
     (if insight
       [:div {:id "feed-insights-list" :class "space-y-2"}
        [:a {:href (str "/insights/" (:id insight))
             :class "card bg-base-200 shadow-sm hover:shadow-md transition-shadow"}
         [:div {:class "card-body p-3"}
          [:div {:class "flex items-center gap-2 mb-1"}
           [:span {:class "badge badge-secondary badge-xs"} (category-label (:category insight))]
           [:span {:class "text-xs opacity-50 ml-auto"} (format-date (:created-at insight))]]
          [:p {:class "text-sm line-clamp-1"} (:context insight)]
          (when-let [first-advice (first (:advice-to-self insight))]
            [:p {:class "text-sm opacity-70 line-clamp-1"} first-advice])]]
        [:div {:class "mt-2"}
         [:a {:href (str "/insights/new?state_label=" state-label)
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-3"}
          [:svg {:xmlns "http://www.w3.org/2000/svg"
                 :width 18 :height 18 :viewBox "0 0 24 24"
                 :fill "none" :stroke "currentColor"
                 :stroke-width 2.5 :stroke-linecap "round" :stroke-linejoin "round"}
           [:path {:d "M12 5v14M5 12h14"}]]
          (i18n/t :insights/widget-record)]]]
       ;; Онбординг (fallback, OQ3)
       [:div {:class "text-center py-6 px-4"}
        [:p {:class "text-sm opacity-70 mb-3"}
         (i18n/t :insights/onboarding-state {:state (state-label-text state-label)})]
        [:a {:href (str "/insights/new?state_label=" state-label)
             :class "btn btn-primary btn-sm h-11 min-h-11 px-4"}
         (i18n/t :insights/onboarding-state-create)]])]))
