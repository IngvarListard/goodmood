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

(defn- confidence-dots
  "Дот-шкала уверенности находки вместо текстового бейджа (design D4):
   3 точки — high 3/primary, medium 2/warning, low 1/muted. nil — не
   рендерится (фикс «{Missing key …}» у confidence-badge)."
  [confidence]
  (when confidence
    (let [filled (case confidence "high" 3 "medium" 2 "low" 1 0)
          dot (case confidence
                "high" "bg-primary"
                "medium" "bg-warning"
                "bg-base-content/30")]
      [:span {:class "flex items-center gap-1 shrink-0"
              :title (i18n/t (keyword "ai" (str "confidence-" confidence)))}
       (for [i (range 3)]
         [:span {:key i
                 :class (str "w-1.5 h-1.5 rounded-full "
                             (if (< i filled)
                               dot
                               "bg-base-content/15"))}])])))

(defn- feedback-buttons
  "Иконочные кнопки фидбека находки (design D4): «не релевантно»
   (x-circle) и «уже знал» (check-circle) для корреляций, «пожаловаться»
   (flag) для советов. aria-label/title несут текст, контракт тот же.
   csrf-token, finding-id, kind — 'correlation'|'advice'."
  [csrf-token finding-id kind]
  (let [buttons
        (if (= kind "advice")
          [{:feedback "report" :label (i18n/t :ai/report-advice) :icon "flag"}]
          [{:feedback "irrelevant" :label (i18n/t :ai/not-relevant) :icon "x-circle"}
           {:feedback "already-known" :label (i18n/t :ai/already-known)
            :icon "check-circle"}])]
    [:div {:class "flex items-center gap-1 mt-2"}
     (for [{:keys [feedback label icon]} buttons]
       ^{:key feedback}
       [:button {:type "button"
                 :class "btn btn-ghost btn-sm h-9 min-h-9 w-9 px-0"
                 :title label
                 :aria-label label
                 :hx-post (str "/ai/findings/" finding-id "/feedback")
                 :hx-ext "json-enc"
                 :hx-target "closest section"
                 :hx-swap "outerHTML"
                 :hx-vals (str "{\"__anti-forgery-token\": \"" csrf-token
                               "\", \"feedback\": \"" feedback "\"}")}
        (icons/svg icon {:class "w-4 h-4"})])]))

(defn ai-correlations
  "Секция «AI-корреляции» для /feed. findings — вектор находок type=correlation.
   Карточка — единая анатомия AI-находок (design D4): шапка в одну строку
   (иконка, тип, дот-шкала уверенности, крестик-скрытие), тело — название и
   описание с ограничением строк, действия — иконочный фидбек.
   Если находок нет — фрагмент пуст (ничего не рендерится)."
  [csrf-token findings]
  (when (seq findings)
    [:section {:class "mb-4" :id "ai-correlations" :data-testid "ai-correlations"}
     [:h2 {:class "text-sm font-medium text-base-content/60 mb-2 uppercase tracking-wide"}
      (i18n/t :ai/correlations-title)]
     [:div {:class "space-y-2"}
      (for [f findings]
        ^{:key (:id f)}
        [:div {:class "relative rounded-[18px] bg-base-200 shadow-sm p-3"
               :data-testid (str "ai-correlation-" (:id f))}
         [:div {:class "flex items-center gap-2"}
          (icons/svg "link" {:class "text-primary w-4 h-4"})
          [:span {:class "text-[13px] text-base-content/60 flex-1 min-w-0"}
           (i18n/t :ai/finding-correlation)]
          (confidence-dots (:confidence f))
          [:button {:type "button"
                    :class "btn btn-ghost btn-sm h-9 min-h-9 w-9 px-0"
                    :title (i18n/t :ai/dismiss)
                    :aria-label (i18n/t :ai/dismiss)
                    :_ "on click remove closest [data-testid^='ai-correlation']"}
           (icons/svg "x-mark" {:class "w-4 h-4"})]]
         [:div {:class "mt-1"}
          [:p {:class "text-sm font-medium"} (get-in f [:content :title])]
          (when-let [desc (get-in f [:content :description])]
            [:p {:class "text-sm opacity-80 line-clamp-3 break-words"} desc])]
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
           (confidence-dots (:confidence (first findings))))]
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
  "AI-совет из своих инсайтов — единая анатомия AI-находок (design D4):
   шапка в одну строку (лампочка, тип, дот-шкала уверенности, крестик),
   тело — совет с ограничением строк и раскрытием «почему», действия —
   ссылки на исходные инсайты и иконочный фидбек.
   findings — массив type=advice. Если пусто — фрагмент не рендерится."
  [csrf-token findings]
  (when-let [f (first findings)]
    (let [{:keys [message explanation]} (:content f)]
      [:section {:class "mb-4" :id "ai-advice" :data-testid "ai-advice"}
       [:div {:class "relative rounded-[18px] bg-base-200 shadow-sm p-3"}
        [:div {:class "gm-accent-stripe"}]
        [:div {:class "flex items-center gap-2"}
         (icons/svg "light-bulb" {:class "text-primary w-4 h-4"})
         [:span {:class "text-[13px] text-base-content/60 flex-1 min-w-0"}
          (i18n/t :ai/advice-title)]
         (confidence-dots (:confidence f))
         [:button {:type "button"
                   :class "btn btn-ghost btn-sm h-9 min-h-9 w-9 px-0"
                   :title (i18n/t :toast/dismiss)
                   :aria-label (i18n/t :toast/dismiss)
                   :_ "on click remove #ai-advice"}
          (icons/svg "x-mark" {:class "w-4 h-4"})]]
        [:div {:id "ai-advice-text" :class "mt-1 line-clamp-3"}
         [:p {:class "text-sm leading-relaxed break-words"} message]]
        [:button {:type "button"
                  :class "btn btn-ghost btn-xs text-primary px-1"
                  :_ "on click remove .line-clamp-3 from #ai-advice-text then add .hidden to me"}
         (i18n/t :toast/hint-more)]
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
           :class "card bg-base-200 border border-base-300 p-4"}
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
  "Novel AI-совет (общая DBT/CBT техника) — единая анатомия AI-находок
   (design D4): шапка (sparkles, тип, дот-шкала, крестик), note «не из твоих
   записей», тело — совет, действия — иконочный фидбек. findings — вектор
   type=advice с content.novel=true. Пусто — фрагмент не рендерится."
  [csrf-token findings]
  (when-let [f (first findings)]
    (let [{:keys [message explanation]} (:content f)]
      [:section {:class "mb-4" :id "ai-novel-advice" :data-testid "ai-novel-advice"}
       [:div {:class "relative rounded-[18px] bg-base-200 shadow-sm p-3"}
        [:div {:class "flex items-center gap-2"}
         (icons/svg "sparkles" {:class "text-accent w-4 h-4"})
         [:span {:class "text-[13px] text-base-content/60 flex-1 min-w-0"}
          (i18n/t :ai/novel-title)]
         (confidence-dots (:confidence f))
         [:button {:type "button"
                   :class "btn btn-ghost btn-sm h-9 min-h-9 w-9 px-0"
                   :title (i18n/t :toast/dismiss)
                   :aria-label (i18n/t :toast/dismiss)
                   :_ "on click remove #ai-novel-advice"}
          (icons/svg "x-mark" {:class "w-4 h-4"})]]
        [:span {:class "badge badge-accent badge-outline badge-xs mt-1"}
         (i18n/t :ai/novel-note)]
        [:div {:id "ai-novel-advice-text" :class "mt-1 line-clamp-3"}
         [:p {:class "text-sm leading-relaxed break-words"} message]
         (when explanation
           [:p {:class "text-sm text-base-content/70 italic"} explanation])]
        [:button {:type "button"
                  :class "btn btn-ghost btn-xs text-primary px-1"
                  :_ (str "on click remove .line-clamp-3 from #ai-novel-advice-text"
                          " then add .hidden to me")}
         (i18n/t :toast/hint-more)
         (feedback-buttons csrf-token (:id f) "advice")]]])))

;; ──────────────────────────────────────────────────────────────
;; AI-чат (Decision 6.3) — on-demand, disclaimer, кризис-ресурс
;; ──────────────────────────────────────────────────────────────

(defn- chat-bubble
  "Пузырь сообщения чата: assistant слева с аватаром-лампочкой,
   user справа с градиентным bubble (по макету)."
  [{:keys [id role content]}]
  (let [user? (= role "user")]
    [:div {:class (if user? "chat chat-end" "chat chat-start")}
     (when-not user?
       [:div {:class "chat-image-avatar bg-base-300 border border-base-content/10 text-secondary"}
        (icons/svg "light-bulb")])
     [:div {:class (if user?
                     "chat-bubble chat-bubble-primary gm-gradient border-0 text-sm break-words min-w-0"
                     "chat-bubble bg-base-300 text-sm break-words min-w-0")}
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

(defn chat-panel
  "Контент модалки ассистента (ответ GET /ai/chat) — заменяет #assistant-body
   через outerHTML. Шапка с drag-handle, скроллируемая история (#chat-response),
   постоянная строка дисклеймера и закреплённый composer (flex-низ модалки).
   Дисклеймер — всегда видимая мелкая строка, без гейта с подтверждением;
   второй аргумент (бывший show-disclaimer?) оставлен для совместимости
   с существующими роутами и не используется."
  [csrf-token _show-disclaimer? messages]
  [:div {:id "assistant-body"
         :class "flex flex-col h-full bg-base-200"}
   ;; Drag-handle: визуальный, клик закрывает модалку
   [:button {:type "button"
             :class "mx-auto mt-2 mb-1 h-1.5 w-12 rounded-full bg-base-content/25 shrink-0"
             :aria-label (i18n/t :ai/chat-close)
             :_ "on click call #assistant-modal.close()"}]
    [:div {:class "flex items-start justify-between px-5 pb-3 border-b border-base-300 shrink-0"}
     [:div
      [:h2 {:class "text-xl font-bold"} (i18n/t :ai/assistant-title)]
      [:p {:class "text-xs text-base-content/60 mt-0.5"}
       (i18n/t :ai/assistant-subtitle)]]
     [:div {:class "flex items-center gap-1"}
      [:button {:type "button"
                :class "btn btn-ghost btn-circle btn-sm"
                :title (i18n/t :ai/chat-new)
                :aria-label (i18n/t :ai/chat-new)
                :hx-post "/ai/chat/new"
                :hx-ext "json-enc"
                :hx-vals (str "{\"__anti-forgery-token\": \"" csrf-token "\"}")
                :hx-target "#assistant-body"
                :hx-swap "outerHTML"}
       (icons/svg "plus")]
      [:button {:type "button"
                :class "btn btn-ghost btn-circle btn-sm"
                :aria-label (i18n/t :ai/chat-close)
                :_ "on click call #assistant-modal.close()"}
       (icons/svg "x-mark")]]]
   [:div {:class "flex-1 overflow-y-auto px-4 py-3 min-h-0"}
    (chat-response {:messages messages})]
   [:p {:class "text-[10px] text-center text-base-content/50 px-6 pt-1 pb-2 shrink-0"}
    (i18n/t :ai/chat-disclaimer)]
   ;; Оптимистичный UI (design D2): hyperscript на submit мгновенно рисует
   ;; бабл пользователя (textContent — без HTML-инъекции), индикатор
   ;; «печатает» и чистит инпут; серверный свап #chat-response заменяет
   ;; фрагмент правдой из БД. Ошибка запроса — снимает индикатор (бабл
   ;; остаётся, как в спеке).
   [:form {:class (str "flex items-center gap-2 px-4 pt-2 flex-none "
                       "pb-[max(1rem,env(safe-area-inset-bottom))]")
           :hx-post "/ai/chat"
           :hx-ext "json-enc"
           :hx-target "#chat-response"
           :hx-swap "outerHTML"
           :_ "on submit
                 if #chat-input.value is not \"\" then
                   make a <div/> called userWrap
                   set userWrap's className to \"chat chat-end\"
                   make a <div/> called userBub
                   set userBub's className to \"chat-bubble chat-bubble-primary gm-gradient border-0 text-sm\"
                   set userBub's textContent to #chat-input.value
                   put userBub at the end of userWrap
                   put userWrap at the end of #chat-response
                   make a <div/> called typing
                   set typing's innerHTML to \"<div class='chat chat-start' id='chat-typing' data-testid='chat-typing'><div class='chat-bubble bg-base-300 flex items-center gap-1.5 py-3'><span class='w-1.5 h-1.5 rounded-full bg-primary animate-bounce'></span><span class='w-1.5 h-1.5 rounded-full bg-primary animate-bounce [animation-delay:0.2s]'></span><span class='w-1.5 h-1.5 rounded-full bg-primary animate-bounce [animation-delay:0.4s]'></span></div></div>\"
                   put typing at the end of #chat-response
                   set #chat-input.value to \"\"
                   set #chat-response's scrollTop to #chat-response's scrollHeight
                 end
               on htmx:responseError
                 remove #chat-typing"}
    [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
    [:input {:type "text" :name "message" :id "chat-input" :required true
             :class "input input-bordered rounded-full flex-1 min-w-0"
             :placeholder (i18n/t :ai/chat-placeholder)}]
    [:button {:type "submit"
              :class "btn btn-circle border-0 gm-gradient gm-glow text-white"
              :aria-label (i18n/t :ai/chat-send)}
     (icons/svg "paper-airplane")]]])

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