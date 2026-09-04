(ns app.views.ai
  (:require [app.i18n :as i18n]
            [app.icons :as icons]
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
  "Бейдж уровня уверенности находки. Medium — outline amber (макет lenta:
   тёплая рамка на тёмном фоне), high/low — стандартные daisyUI-классы."
  [confidence]
  (let [cls (case confidence
              "high" "badge-success"
              "medium" "badge-outline text-warning border-warning"
              "low" "badge-ghost"
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
     [:h2 {:class "text-sm font-medium text-base-content/60 mb-2 uppercase tracking-wide"}
      (i18n/t :ai/correlations-title)]
     [:div {:class "space-y-2"}
      (for [f findings]
        ^{:key (:id f)}
        [:div {:class "rounded-[18px] border border-primary/50 bg-base-200 shadow-sm p-3"}
         [:div {:class "flex items-center gap-2 mb-1"}
          [:span {:class "text-sm font-medium"} (get-in f [:content :title])]
          (confidence-badge (:confidence f))]
         (when-let [desc (get-in f [:content :description])]
           [:p {:class "text-sm opacity-80"} desc])
         (feedback-buttons csrf-token (:id f) "correlation")])]]))

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
           [:span {:class "text-xs text-base-content/60"} (i18n/t :ai/label-proposal)])
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
  "AI-совет из своих инсайтов в виде insight-карточки (макет lenta 74–88):
   rounded-[18px] с primary-бордером, лампочка в кружке bg-primary/10,
   бейдж уверенности справа от заголовка. findings — массив type=advice.
   Если пусто — фрагмент не рендерится. Показывает ссылки на исходные
   инсайты."
  [csrf-token findings]
  (when-let [f (first findings)]
    (let [{:keys [message explanation]} (:content f)]
      [:section {:class "mb-4" :id "ai-advice" :data-testid "ai-advice"}
       [:div {:class "rounded-[18px] border border-primary/50 bg-base-200 shadow-sm p-4"}
        [:div {:class "flex items-start gap-3"}
         [:div {:class "w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center shrink-0"}
          (icons/svg "light-bulb" {:class "text-primary"})]
         [:div {:class "flex-1 min-w-0"}
          [:div {:class "flex items-start justify-between gap-2 min-w-0"}
           [:span {:class "text-[13px] text-base-content/60"}
            (i18n/t :ai/advice-title)]
           (confidence-badge (:confidence f))]]]
        [:p {:class "text-sm opacity-90 mt-3 leading-relaxed"} message]
        (when explanation
          [:div {:class "flex items-center gap-2 mt-1"}
           [:button {:type "button"
                     :class "btn btn-ghost btn-xs h-9 min-h-9 px-2"
                     :_ "on click toggle .hidden on #ai-advice-why-detail"}
            (i18n/t :ai/advice-why-button)]
           [:p {:id "ai-advice-why-detail"
                :class "ai-advice-why hidden text-sm text-base-content/70 italic"}
            (i18n/t :ai/advice-why {:explanation explanation})]])
        (when (seq (:source-refs f))
          [:div {:class "mt-2 flex flex-wrap gap-2"}
           (for [sid (:source-refs f)]
             ^{:key sid}
             [:a {:href (str "/insights/" sid)
                  :class "link link-primary text-xs"}
              (i18n/t :ai/from-insights)])])
        (feedback-buttons csrf-token (:id f) "advice")]])))

(defn ai-settings-saved
  "Фрагмент статуса «Сохранено» для настроек AI (свапается в #ai-settings-status)."
  []
  [:div {:class "alert alert-success shadow-sm"}
   [:span {:class "text-sm"} (i18n/t :notifications/saved)]])

(defn- ai-toggle-input
  "Тумблер настроек AI: автосохраняется при изменении (hx-trigger change),
   отправляя текущую форму целиком (hx-include), а не только поле."
  [name checked & [extra]]
  [:input (merge {:type "checkbox" :name name
                  :class "toggle toggle-primary" :checked checked
                  :hx-trigger "change"
                  :hx-post "/settings/ai"
                  :hx-include "#ai-settings-form"
                  :hx-target "#ai-settings-status"
                  :hx-swap "innerHTML"}
                 extra)])

(defn ai-settings-section
  "Секция «AI» для /settings: master-toggle + per-function тумблеры (вкл.
   novel-советы и предупреждения эпизодов — оба opt-in, default off)."
  [csrf-token {:keys [master-enabled correlations-enabled labels-enabled
                      advice-enabled allow-novel-advice episode-warning-enabled]}]
  [:div {:class "mb-6"}
   [:h2 {:class "text-sm font-medium text-base-content/60 mb-1 uppercase tracking-wide"}
    (i18n/t :ai/settings-title)]
   [:p {:class "text-xs text-base-content/60 mb-3"} (i18n/t :ai/settings-subtitle)]
   [:div {:id "ai-settings-status" :class "mb-2"}]
   [:form {:id "ai-settings-form"
           :hx-post "/settings/ai"
           :hx-target "#ai-settings-status"
           :hx-swap "innerHTML"
           :class "card bg-base-200 p-4"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
    [:div {:class "divide-y divide-base-300"}
     [:div {:class "flex items-center justify-between gap-3 py-3 first:pt-0"}
      [:span {:class "label-text"} (i18n/t :ai/master-toggle)]
      (ai-toggle-input "master_enabled" (not= master-enabled 0) nil)]
     [:div {:class "flex items-center justify-between gap-3 py-3"}
      [:span {:class "label-text"} (i18n/t :ai/toggle-correlations)]
      (ai-toggle-input "correlations_enabled" (not= correlations-enabled 0) nil)]
     [:div {:class "flex items-center justify-between gap-3 py-3"}
      [:span {:class "label-text"} (i18n/t :ai/toggle-labels)]
      (ai-toggle-input "labels_enabled" (not= labels-enabled 0) nil)]
     [:div {:class "flex items-center justify-between gap-3 py-3"}
      [:span {:class "label-text"} (i18n/t :ai/toggle-advice)]
      (ai-toggle-input "advice_enabled" (not= advice-enabled 0) nil)]
     [:div {:class "flex items-center justify-between gap-3 py-3"}
      [:span {:class "label-text"} (i18n/t :ai/toggle-novel-advice)]
      (ai-toggle-input "allow_novel_advice" (not= allow-novel-advice 0)
                       {:data-testid "allow-novel-advice-toggle"})]
     [:div {:class "flex items-center justify-between gap-3 py-3 last:pb-0"}
      [:span {:class "label-text"} (i18n/t :ai/toggle-episode-warning)]
      (ai-toggle-input "episode_warning_enabled" (not= episode-warning-enabled 0)
                       {:data-testid "episode-warning-toggle"})]]
    [:button {:type "submit" :class "btn btn-primary w-full h-12 mt-4"}
     (i18n/t :notifications/save)]]])

;; ──────────────────────────────────────────────────────────────
;; Novel advice (Decision 6.2) — opt-in, помечен «не из твоих записей»
;; ──────────────────────────────────────────────────────────────

(defn ai-novel-advice
  "Novel AI-совет (общая DBT/CBT техника). findings — вектор type=advice с
   content.novel=true. Помечен «не из твоих записей» (data-testid
   ai-novel-advice). Пусто — фрагмент не рендерится."
  [csrf-token findings]
  (when-let [f (first findings)]
    (let [{:keys [message explanation]} (:content f)]
      [:section {:class "mb-4" :id "ai-novel-advice" :data-testid "ai-novel-advice"}
       [:div {:class "card bg-base-200 border-accent/20 shadow-sm"}
        [:div {:class "card-body p-3"}
         [:div {:class "flex items-center gap-2 mb-1"}
          [:span {:class "text-xs uppercase tracking-wide text-base-content/60"}
           (i18n/t :ai/novel-title)]
          (confidence-badge (:confidence f))]
         [:span {:class "badge badge-accent badge-outline badge-xs"}
          (i18n/t :ai/novel-note)]
         [:p {:class "text-sm opacity-90 mt-2"} message]
         (when explanation
           [:p {:class "text-sm text-base-content/70 italic mt-1"} explanation])
         (feedback-buttons csrf-token (:id f) "advice")]]])))

;; ──────────────────────────────────────────────────────────────
;; AI-чат (Decision 6.3) — on-demand, disclaimer, кризис-ресурс
;; ──────────────────────────────────────────────────────────────

(defn- chat-bubble
  "Пузырь сообщения чата: user справа, assistant слева."
  [{:keys [id role content]}]
  (let [user? (= role "user")]
    [:div {:class (if user? "chat chat-end" "chat chat-start")}
     [:div {:class (str "chat-bubble text-sm"
                        (when user? " chat-bubble-primary"))}
      content]]))

(defn- crisis-alert
  "Алерт с ресурсами проф. помощи (появляется при кризис-детекции)."
  []
  [:div {:class "alert alert-error shadow-sm" :data-testid "ai-chat-crisis"}
   [:div {:class "flex flex-col gap-1 text-sm"}
    [:p {:class "font-medium"} (i18n/t :ai/chat-crisis-title)]
    [:p (i18n/t :ai/chat-crisis-hotline)]
    [:p (i18n/t :ai/chat-crisis-112)]]])

(defn chat-response
  "Фрагмент сообщений чата для #chat-response. messages — вектор сообщений
   из ai_chat_messages. crisis? — показывать алерт с проф. помощью сверху.
   error? — показать фолбэк-пузырь ассистента (не сохраняется в БД)."
  [{:keys [crisis? error? messages]}]
  [:div {:id "chat-response" :class "space-y-2" :data-testid "chat-response"}
   (when crisis? (crisis-alert))
   (for [m messages]
     ^{:key (:id m)}
     (chat-bubble m))
   (when error?
     [:div {:class "chat chat-start" :data-testid "chat-error-bubble"}
      [:div {:class "chat-bubble chat-bubble-error text-sm"}
       (i18n/t :ai/chat-error-fallback)]])])

(defn- chat-disclaimer
  "Disclaimer «не заменяет терапию» при первом открытии чата."
  [csrf-token]
  [:div {:class "alert alert-info shadow-sm" :data-testid "ai-chat-disclaimer"}
   [:div {:class "flex flex-col gap-2 w-full"}
    [:p {:class "text-sm"} (i18n/t :ai/chat-disclaimer)]
    [:div {:class "flex gap-2"}
     [:button {:type "button"
               :class "btn btn-primary btn-sm h-11 min-h-11"
               :hx-post "/ai/chat/disclaimer"
               :hx-ext "json-enc"
               :hx-target "#ai-chat-panel"
               :hx-swap "outerHTML"
               :hx-vals (str "{\"__anti-forgery-token\": \"" csrf-token "\"}")}
      (i18n/t :ai/chat-disclaimer-accept)]]]])

(defn chat-panel
  "Панель AI-чата (открывается по кнопке на /feed). show-disclaimer? — при
   первом открытии (dismiss через POST /ai/chat/disclaimer). messages — вектор
   уже сохранённых сообщений для показа истории."
  [csrf-token show-disclaimer? messages]
  [:div {:id "ai-chat-panel" :data-testid "ai-chat"}
   (when show-disclaimer? (chat-disclaimer csrf-token))
   [:div {:class "card bg-base-200 shadow-sm"}
    [:div {:class "card-body p-3"}
     [:div {:class "flex items-center justify-between mb-2"}
      [:h2 {:class "text-sm font-medium uppercase tracking-wide"}
       (i18n/t :ai/chat-title)]
      [:button {:type "button"
                :class "btn btn-ghost btn-sm h-9 min-h-9 px-2"
                :aria-label (i18n/t :ai/chat-close)
                :_ "on click remove #ai-chat-panel"}
       [:svg {:xmlns "http://www.w3.org/2000/svg"
              :width 18 :height 18 :viewBox "0 0 24 24"
              :fill "none" :stroke "currentColor"
              :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M6 6l12 12M18 6L6 18"}]]]]
     (chat-response {:messages messages})
     [:form {:class "mt-2 flex gap-2"
             :hx-post "/ai/chat"
             :hx-ext "json-enc"
             :hx-target "#chat-response"
             :hx-swap "outerHTML"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
      [:textarea {:name "message" :rows "2" :required true
                  :class "textarea textarea-bordered flex-1 min-w-0"
                  :placeholder (i18n/t :ai/chat-placeholder)}]
      [:button {:type "submit" :class "btn btn-primary h-auto min-h-11 px-4"}
       (i18n/t :ai/chat-send)]]]]])

;; ──────────────────────────────────────────────────────────────
;; Episode warnings (Фаза 7) — opt-in, мягкий copy, guardrails
;; ──────────────────────────────────────────────────────────────

(defn crisis-resource-banner
  "Баннер ресурсов профессиональной помощи (кризис приоритетнее
   предупреждения об эпизоде)."
  []
  [:div {:class "alert alert-error shadow-sm mb-4" :data-testid "crisis-resource"}
   [:div {:class "flex flex-col gap-1 text-sm"}
    [:p {:class "font-medium"} (i18n/t :crisis/resource-title)]
    [:p (i18n/t :crisis/resource-support)]
    [:p (i18n/t :ai/chat-crisis-hotline)]
    [:p (i18n/t :ai/chat-crisis-112)]]])

(defn episode-warning
  "Мягкое предупреждение о возможном начале эпизода (opt-in, confidence > 0.75).
   Не тревожное: поддерживающая формулировка, объяснение паттерна, лёгкое
   выключение и false-alarm feedback."
  [csrf-token warning]
  (when warning
    (let [w-id (:id warning)
          why-id (str "episode-warning-why-" w-id)]
      [:div {:class "alert alert-info shadow-sm mb-4"
             :id "episode-warning"
             :data-testid "episode-warning"}
       [:div {:class "w-full"}
        [:p {:class "text-sm font-medium"}
         (i18n/t :ai/episode-warning-title)]
        [:p {:class "text-sm opacity-80 mt-1"}
         (:pattern-description warning)]
        [:div {:class "mt-2 flex flex-wrap gap-2"}
         [:button {:type "button"
                   :class "btn btn-ghost btn-xs h-9 min-h-9 px-2"
                   :_ (str "on click toggle .hidden on #" why-id)}
          (i18n/t :ai/episode-warning-why)]
         [:button {:type "button"
                   :class "btn btn-ghost btn-xs h-9 min-h-9 px-2"
                   :hx-post "/ai/episode-warning/disable"
                   :hx-ext "json-enc"
                   :hx-target "closest [data-testid='episode-warning']"
                   :hx-swap "outerHTML"
                   :hx-vals (json/generate-string
                             {"__anti-forgery-token" csrf-token})}
          (i18n/t :ai/episode-warning-disable)]
         [:button {:type "button"
                   :class "btn btn-ghost btn-xs h-9 min-h-9 px-2"
                   :hx-post (str "/ai/episode-warning/" w-id "/feedback")
                   :hx-ext "json-enc"
                   :hx-target "closest [data-testid='episode-warning']"
                   :hx-swap "outerHTML"
                   :hx-vals (json/generate-string
                             {"__anti-forgery-token" csrf-token
                              "feedback" "false_alarm"})}
          (i18n/t :ai/episode-warning-false-alarm)]]
        [:p {:id why-id
             :class "episode-warning-why hidden text-xs text-base-content/60 mt-1"}
         (i18n/t :ai/episode-warning-why-note)]]])))

(defn episode-warning-slot
  "Polling-слот для /feed: запрашивает /ai/episode-warning, заменяет себя
   предупреждением/кризис-баннером или исчезает (hx-swap outerHTML).
   Только every-таймер (без load): ответ-слот не порождает цикл мгновенных
   запросов."
  []
  [:div {:id "episode-warning-slot"
         :hx-get "/ai/episode-warning"
         :hx-trigger "every 5s"
         :hx-swap "outerHTML"}])

(defn episode-warning-fragment
  "Фрагмент секции предупреждений для /feed: кризис-баннер приоритетнее
   паттерна. signal — map {:crisis? :enabled? :warning}."
  [csrf-token {:keys [crisis? enabled? warning]}]
  (cond
    crisis? (crisis-resource-banner)
    warning (episode-warning csrf-token warning)
    enabled? (episode-warning-slot)
    :else nil))