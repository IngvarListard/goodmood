(ns app.views.ai
  (:require [app.i18n :as i18n]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def state-labels
  "Список стандартных state_label розы ветров (raw ключи, без :state/)."
  ["mixed" "anxiety" "elevated" "low" "balanced" "neutral"])

(defn- state-label-text
  "Локализованный текст стандартного state_label; для свободного текста
   AI-предложения возвращает сам ярлык (fallback при отсутствии ключа)."
  [state-label]
  (let [raw (if (str/starts-with? state-label "state/")
              (subs state-label 6)
              state-label)]
    (if (str/blank? raw)
      ""
      (let [translated (i18n/t (keyword "state" raw))]
        (if (string? translated) translated raw)))))

(defn- ai-label-option-text
  "Локализованный текст для опции состояния в AI-виджете.
   По контракту e2e elevated-состояние в EN показывается как 'high', в RU — как
   'подъём'; остальные состояния — стандартный локализованный ярлык."
  [state-label]
  (let [raw (if (str/starts-with? state-label "state/")
              (subs state-label 6)
              state-label)]
    (case raw
      "elevated" (i18n/t :ai/label-high)
      (state-label-text raw))))

(defn- confidence-badge
  "Бейдж уровня уверенности находки."
  [confidence]
  (let [cls (case confidence
              "high"   "badge-success"
              "medium" "badge-warning"
              "low"    "badge-ghost"
              "badge-ghost")]
    [:span {:class (str "badge badge-sm " cls)}
     (i18n/t (keyword "ai" (str "confidence-" confidence)))]))

(defn- feedback-buttons
  "Кнопки фидбека находки: «не релевантно» и «уже знал» (для корреляций)
   или «пожаловаться» (для советов). csrf-token, finding-id, kind —
   'correlation'|'advice'."
  [csrf-token finding-id kind]
  (let [buttons
        (if (= kind "advice")
          [{:feedback "report" :label (i18n/t :ai/report-advice)}]
          [{:feedback "irrelevant" :label (i18n/t :ai/not-relevant)}
           {:feedback "already-known" :label (i18n/t :ai/already-known)}])]
    [:div {:class "flex flex-wrap gap-2 mt-2"}
     (for [{:keys [feedback label]} buttons]
       ^{:key feedback}
       [:button {:type "button"
                 :class "btn btn-ghost btn-xs h-9 min-h-9 px-2"
                 :hx-post (str "/ai/findings/" finding-id "/feedback")
                 :hx-ext "json-enc"
                 :hx-target "closest section"
                 :hx-swap "outerHTML"
                 :hx-vals (str "{\"__anti-forgery-token\": \"" csrf-token
                               "\", \"feedback\": \"" feedback "\"}")}
        label])]))

(defn ai-correlations
  "Секция «AI-корреляции» для /feed. findings — вектор находок type=correlation.
   Если находок нет — фрагмент пуст (ничего не рендерится)."
  [csrf-token findings]
  (when (seq findings)
    [:section {:class "mb-4" :id "ai-correlations" :data-testid "ai-correlations"}
     [:h2 {:class "text-sm font-medium opacity-60 mb-2 uppercase tracking-wide"}
      (i18n/t :ai/correlations-title)]
     [:div {:class "space-y-2"}
      (for [f findings]
        ^{:key (:id f)}
        [:div {:class "card bg-base-200 shadow-sm"}
         [:div {:class "card-body p-3"}
          [:div {:class "flex items-center gap-2 mb-1"}
           [:span {:class "text-sm font-medium"} (get-in f [:content :title])]
           (confidence-badge (:confidence f))]
          (when-let [desc (get-in f [:content :description])]
            [:p {:class "text-sm opacity-80"} desc])
          (feedback-buttons csrf-token (:id f) "correlation")]])]]))

(defn- label-action-button
  "Кнопка в меню AI-ярлыка: отправляет выбранный label на /ai/label/apply
   (или reject для отклонения) и свапает виджет целиком."
  [csrf-token value label]
  (let [attrs (if value
                {"__anti-forgery-token" csrf-token
                 "label" value}
                {"__anti-forgery-token" csrf-token
                 "reject" "true"})]
    [:li
     [:button {:type "button"
               :class "w-full text-left"
               :hx-post "/ai/label/apply"
               :hx-ext "json-enc"
               :hx-target "closest [data-testid='ai-state-label']"
               :hx-swap "outerHTML"
               :hx-vals (json/generate-string attrs)}
      label]]))

(defn ai-state-label
  "AI-ярлык состояния: интерактивный виджет-выпадашка. Показывает
   предложенный AI ярлык (или применённый пользователем) и меню:
   «принять» AI-предложение, стандартные state_label розы ветров, «отклонить».
   findings — массив type=label (берём первый); applied-label — применённый
   пользователем ярлык (raw ключ или текст) или nil.
   Пусто — фрагмент не рендерится."
  ([csrf-token findings]
   (ai-state-label csrf-token findings nil))
  ([csrf-token findings applied-label]
   (when-let [f (or (first findings) (when applied-label {:content {:label applied-label}}))]
     (let [{:keys [label] :as content} (:content f)
           display (if applied-label
                     (ai-label-option-text applied-label)
                     (or label ""))]
       [:div {:class "dropdown dropdown-end mt-1"
              :id "ai-state-label"
              :data-testid "ai-state-label"}
        [:div {:tabindex 0
               :role "button"
               :class "btn btn-ghost btn-xs h-9 min-h-9 px-2 gap-1 normal-case"
               :aria-label (str (i18n/t :ai/label-proposal) " " display)}
         (when-not applied-label
           [:span {:class "text-xs opacity-60"} (i18n/t :ai/label-proposal)])
         [:span {:class "text-sm"} display]
         (when (and (not applied-label) (first findings))
           (confidence-badge (:confidence (first findings))))]
        [:ul {:tabindex 0
              :class "dropdown-content menu bg-base-100 rounded-box z-50 w-56 p-2 shadow"}
         (when (and (not applied-label) (:explanation content))
           [:li {:class "menu-title"}
            (str (i18n/t :ai/label-why) " " (:explanation content))])
         (when (and (not applied-label) (:label content))
           (label-action-button csrf-token label (str (i18n/t :ai/label-accept) ": " label)))
         [:li {:class "menu-title"} (i18n/t :ai/label-choose)]
         (for [sl state-labels]
           ^{:key sl}
           (label-action-button csrf-token sl (ai-label-option-text sl)))
         [:div {:class "divider my-1"}]
         (label-action-button csrf-token nil (i18n/t :ai/label-reject))]]))))

(defn ai-advice
  "AI-совет из своих инсайтов. findings — массив type=advice. Если пусто —
   фрагмент не рендерится. Показывает ссылки на исходные инсайты."
  [csrf-token findings]
  (when-let [f (first findings)]
    (let [{:keys [message explanation]} (:content f)]
      [:section {:class "mb-4" :id "ai-advice" :data-testid "ai-advice"}
       [:div {:class "card bg-base-200 border-primary/20 shadow-sm"}
         [:div {:class "card-body p-3"}
          [:div {:class "flex items-center gap-2 mb-1"}
           [:span {:class "text-xs uppercase tracking-wide opacity-50"}
            (i18n/t :ai/advice-title)]
           (confidence-badge (:confidence f))]
          [:p {:class "text-sm opacity-90"} message]
          (when explanation
            [:div {:class "flex items-center gap-2 mt-1"}
             [:button {:type "button"
                       :class "btn btn-ghost btn-xs h-9 min-h-9 px-2"
                       :_ "on click toggle .hidden on #ai-advice-why-detail"}
              (i18n/t :ai/advice-why-button)]
             [:p {:id "ai-advice-why-detail"
                  :class "ai-advice-why hidden text-sm opacity-70 italic"}
              (i18n/t :ai/advice-why {:explanation explanation})]])
          (when (seq (:source-refs f))
           [:div {:class "mt-2 flex flex-wrap gap-2"}
            (for [sid (:source-refs f)]
              ^{:key sid}
              [:a {:href (str "/insights/" sid)
                   :class "link link-primary text-xs"}
               (i18n/t :ai/from-insights)])])
         (feedback-buttons csrf-token (:id f) "advice")]]])))

(defn ai-settings-saved
  "Фрагмент статуса «Сохранено» для настроек AI."
  []
  [:div {:id "ai-settings-status" :class "alert alert-success shadow-sm"}
   [:span {:class "text-sm"} (i18n/t :notifications/saved)]])

(defn ai-settings-section
  "Секция «AI» для /settings: master-toggle + per-function тумблеры."
  [csrf-token {:keys [master-enabled correlations-enabled labels-enabled advice-enabled]}]
  [:div {:class "mb-6"}
   [:h2 {:class "text-sm font-medium opacity-60 mb-1 uppercase tracking-wide"}
    (i18n/t :ai/settings-title)]
   [:p {:class "text-xs opacity-50 mb-3"} (i18n/t :ai/settings-subtitle)]
   [:div {:id "ai-settings-status" :class "mb-2"}
    [:form {:hx-post "/settings/ai"
            :hx-target "#ai-settings-status"
            :hx-swap "innerHTML"
            :class "card bg-base-200 p-4"}
     [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
     [:div {:class "flex items-center justify-between gap-3"}
      [:span {:class "label-text"} (i18n/t :ai/master-toggle)]
      [:input {:type "checkbox" :name "master_enabled"
               :class "toggle toggle-primary" :checked (not= master-enabled 0)}]]
     [:div {:class "divider my-1"}]
     [:div {:class "flex items-center justify-between gap-3"}
      [:span {:class "label-text"} (i18n/t :ai/toggle-correlations)]
      [:input {:type "checkbox" :name "correlations_enabled"
               :class "toggle toggle-primary" :checked (not= correlations-enabled 0)}]]
     [:div {:class "divider my-1"}]
     [:div {:class "flex items-center justify-between gap-3"}
      [:span {:class "label-text"} (i18n/t :ai/toggle-labels)]
      [:input {:type "checkbox" :name "labels_enabled"
               :class "toggle toggle-primary" :checked (not= labels-enabled 0)}]]
     [:div {:class "divider my-1"}]
     [:div {:class "flex items-center justify-between gap-3"}
      [:span {:class "label-text"} (i18n/t :ai/toggle-advice)]
      [:input {:type "checkbox" :name "advice_enabled"
               :class "toggle toggle-primary" :checked (not= advice-enabled 0)}]]
     [:button {:type "submit" :class "btn btn-primary w-full h-12 mt-4"}
      (i18n/t :notifications/save)]]]])