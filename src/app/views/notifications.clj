(ns app.views.notifications
  (:require [app.i18n :as i18n]
            [app.icons :as icons]))

(def ^:private default-times
  "Времена слотов по умолчанию (morning/midday/evening)."
  {"morning" "08:00" "midday" "13:00" "evening" "19:00"})

(def ^:private slot-order
  ["morning" "midday" "evening"])

(defn- slot-label
  "Локализованная подпись слота (утро/день/вечер)."
  [slot]
  (i18n/t (keyword "notifications" (str "slot-" slot))))

(defn- enabled?
  "Вернуть true, если слот включён (enabled != 0)."
  [row]
  (and row (not= (:enabled row) 0)))

(defn pending-insight-fragment
  "Самоподдерживающийся polling-фрагмент для /feed: див с hx-get
   /feed/pending-insight, hx-trigger каждые 60s, hx-swap outerHTML.
   Сервер возвращает такой же див (с баннером «пока тебя не было» внутри
   и без него), поэтому polling продолжается бесконечно.
   Когда сервер вернул баннер, hyperscript дополнительно показывает нативное
   уведомление через Notification API (js-interop), если разрешение есть.
   Если разрешения нет — только in-app баннер (graceful fallback)."
  [& [away-body]]
  [:div {:id "pending-insight-poll"
         :hx-get "/feed/pending-insight"
         :hx-trigger "every 60s"
         :hx-swap "outerHTML"
         :_ "on htmx:afterSwap
               if #away-banner exists
                 js(me)
                   const el = me.querySelector('#away-banner p');
                   if ('Notification' in window && Notification.permission === 'granted') {
                     new Notification(document.title || 'Good Mood', { body: el ? el.textContent : '' });
                   }
               end"}
   (when away-body away-body)])

;; ──────────────────────────────────────────────────────────────
;; OOB-toast после сохранения записи (/check-in -> /feed)
;; ──────────────────────────────────────────────────────────────

(defn hint-toast
  "OOB-toast «В таком состоянии тебе помогало…» — фрагмент, возвращаемый
   в POST /entries через hx-swap-oob во #feed-toasts. insight — релевантный
   инсайт (:id :context :advice-to-self) или nil (фрагмент не рендерится)."
  [insight]
  (when insight
    (let [first-advice (first (:advice-to-self insight))]
      [:div {:class "alert alert-info shadow-lg fixed bottom-24 right-4 max-w-sm z-50"
             :id "check-in-hint-toast"
             :hx-swap-oob "true"
             :role "status"}
       [:div {:class "flex items-start gap-3 w-full"}
        (icons/svg "light-bulb")
        [:div {:class "flex-1 min-w-0"}
         [:p {:class "text-sm font-medium"} (i18n/t :toast/hint-title)]
         [:p {:class "text-sm opacity-80 mt-1 line-clamp-1"} (:context insight)]
         (when first-advice
           [:p {:class "text-sm text-base-content/70 mt-1 line-clamp-1"} first-advice])
         [:a {:href (str "/insights/" (:id insight))
              :class "link link-hover text-sm text-base-content/70 mt-1 inline-block"}
          (i18n/t :toast/hint-more)]]
        [:button {:type "button"
                  :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
                  :aria-label (i18n/t :toast/dismiss)
                  :_ "on click add .hidden to me"}
         (icons/svg "x-mark")]]])))

;; ──────────────────────────────────────────────────────────────
;; Баннер «пока тебя не было» (ответ GET /feed/pending-insight)
;; ──────────────────────────────────────────────────────────────

(defn away-banner
  "Баннер «пока тебя не было, был утренний инсайт» — ответ на polling.
   state-label — raw string (например, \"anxiety\"), insight — релевантный инсайт."
  [state-label insight]
  [:div {:class "alert alert-info shadow-sm mb-2" :id "away-banner"}
   [:div {:class "flex items-start gap-3 w-full"}
    (icons/svg "light-bulb")
    [:div {:class "flex-1 min-w-0"}
     [:p {:class "text-sm font-medium"}
      (i18n/t :feed-summary/away
              {:state (i18n/t (keyword "state" state-label))})]
     (when insight
       [:div
        [:p {:class "text-sm opacity-80 mt-1 line-clamp-1"} (:context insight)]
        (when-let [first-advice (first (:advice-to-self insight))]
          [:p {:class "text-sm text-base-content/70 mt-1 line-clamp-1"} first-advice])
        [:a {:href (str "/insights/" (:id insight))
             :class "link link-hover text-sm text-base-content/70 mt-1 inline-block"}
         (i18n/t :toast/hint-more)]])]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
              :aria-label (i18n/t :toast/dismiss)
              :_ "on click remove me"}
     (icons/svg "x-mark")]]])

;; ──────────────────────────────────────────────────────────────
;; Баннер вечерней сводки «на завтра» (OQ6)
;; ──────────────────────────────────────────────────────────────

(defn summary-banner
  "Тонкий баннер «Сводка на завтра готова» — вверху /feed после 18:00.
   Одна строка на тёмной поверхности с левой акцентной полосой (без сплошной
   заливки alert-info); «посмотреть» раскрывает сводный текст. Совет из
   инсайта сюда НЕ встраивается — его единственный источник на ленте теперь
   карточка совета (insights/feed-widget), так убирается дубль. Крестик
   закрывает через POST /notifications/summary-dismiss (dismiss на день)."
  [csrf-token state-label _insight]
  [:div {:class "relative card bg-base-200 border border-base-300 shadow-sm mb-4 px-4 py-2.5"
         :id "summary-banner"}
   [:div {:class "gm-accent-stripe"}]
   [:div {:class "flex items-center gap-2 w-full"}
    [:span {:class "text-base-content/60 shrink-0"}
     (icons/svg "document" {:class "w-5 h-5"})]
    [:p {:class "text-[13px] font-medium flex-1 min-w-0 truncate"}
     (i18n/t :feed-summary/title)]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-9 min-h-9 px-2 text-primary"
              :_ "on click toggle .hidden on #summary-banner-body"}
     (i18n/t :feed-summary/see)]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-9 min-h-9 px-2 shrink-0"
              :aria-label (i18n/t :feed-summary/dismiss)
              :hx-post "/notifications/summary-dismiss"
              :hx-target "this"
              :hx-swap "outerHTML"
              :hx-vals (str "{\"__anti-forgery-token\": \"" csrf-token "\"}")
              :_ "on htmx:afterRequest remove me"}
     (icons/svg "x-mark")]]
   [:div {:id "summary-banner-body" :class "hidden mt-2"}
    [:p {:class "text-sm text-base-content/80"}
     (i18n/t :feed-summary/state
             {:state (i18n/t (keyword "state" state-label))})]
    [:p {:class "text-sm text-base-content/60 italic mt-1"}
     (i18n/t :feed-summary/closing)]]])

;; ──────────────────────────────────────────────────────────────
;; Настройки слотов в /settings
;; ──────────────────────────────────────────────────────────────

(defn settings-section
  "Секция «Уведомления» для /settings: три слота (Утро/День/Вечер),
   каждый — time-input + toggle, одна кнопка «Сохранить».
   slots — вектор строк из БД (:slot :enabled :time)."
  [csrf-token slots]
  (let [slot-by-name (into {} (map (juxt :slot identity) slots))]
    [:div {:class "mb-6"}
     [:h2 {:class "text-sm font-medium text-base-content/60 mb-1 uppercase tracking-wide"}
      (i18n/t :notifications/title)]
     [:p {:class "text-xs text-base-content/60 mb-3"}
      (i18n/t :notifications/subtitle)]
     [:div {:id "notif-status" :class "mb-2"}]
     [:form {:hx-post "/settings/notifications"
             :hx-target "#notif-status"
             :hx-swap "innerHTML"
             :class "card bg-base-200 p-4"}
      [:input {:type "hidden" :name "__anti-forgery-token" :value csrf-token}]
      [:div {:class "divide-y divide-base-300"}
       (for [slot slot-order
             :let [row (get slot-by-name slot)
                   on? (enabled? row)
                   time (or (:time row) (get default-times slot))]]
         ^{:key slot}
         [:div {:class "py-3 first:pt-0 last:pb-0"}
          [:div {:class "flex items-center justify-between gap-3"}
           [:span {:class "label-text"} (slot-label slot)]
           [:div {:class "flex items-center gap-2"}
            [:input {:type "time" :name (str "time_" slot) :value time
                     :class (str "input input-bordered w-24"
                                 (when-not on? " text-base-content/40"))
                     :disabled (not on?)}]
            [:input {:type "checkbox" :name (str "enabled_" slot)
                     :class "toggle toggle-primary"
                     :checked on?}]]]])]
      [:button {:type "submit" :class "btn btn-primary w-full h-12 mt-4"}
       (i18n/t :notifications/save)]]]))

(defn saved-status
  "Статус-фрагмент «Сохранено» — ответ POST /settings/notifications."
  []
  [:div {:id "notif-status" :class "alert alert-success shadow-sm"}
   [:div {:class "flex items-center gap-2 w-full"}
    [:span {:class "text-sm"} (i18n/t :notifications/saved)]
    [:button {:type "button"
              :class "btn btn-ghost btn-sm h-11 min-h-11 px-2 shrink-0"
              :aria-label (i18n/t :toast/dismiss)
              :_ "on click add .hidden to me"}
     (icons/svg "x-mark")]]])

(defn settings-error
  "Мягкий alert ошибки валидации настроек (не «ошибка!»)."
  []
  [:div {:id "notif-status" :class "alert alert-warning shadow-sm"}
   [:span {:class "text-sm"} (i18n/t :notifications/error)]])