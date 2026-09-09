(ns app.views.assistant
  "Глобальный ассистент: FAB + bottom-sheet модалка чата.
   Модалка — нативный <dialog> (DaisyUI modal-bottom); контент чата
   подгружается htmx-ом из GET /ai/chat в #assistant-body (outerHTML)."
  (:require [app.i18n :as i18n]
            [app.icons :as icons]))

(defn- fab-icon
  "Иконка FAB: чат-пузырь с искрой (по макету design/assistant.html)."
  []
  [:span {:class "relative inline-flex items-center justify-center"}
   (icons/svg "chat-bubble-left-ellipsis")
   [:span {:class "absolute -top-0.5 -right-1.5 w-3 h-3"}
    (icons/svg "sparkles" {:class "w-3 h-3"})]])

(defn fab
  "Единственная плавающая кнопка ассистента: клик — hx-get контента чата
   и открытие модалки. fixed над мобильной нижней навигацией: 56×56, отступ
   считается от высоты панели (--gm-nav-h на body), z-60 — выше панели (z-50)."
  []
  [:button {:type "button"
            :data-testid "assistant-fab"
            :aria-label (i18n/t :ai/assistant-open)
            :class (str "btn border-0 gm-gradient gm-glow text-white "
                        "fixed right-4 z-60 w-14 h-14 "
                        "bottom-[calc(var(--gm-nav-h)+env(safe-area-inset-bottom)+12px)]")
            :hx-get "/ai/chat"
            :hx-target "#assistant-body"
            :hx-swap "outerHTML"
            :_ "on htmx:afterRequest call #assistant-modal.showModal()"}
   (fab-icon)])

(defn- loading-body
  "Плейсхолдер #assistant-body до загрузки контента чата (и fallback,
   если GET /ai/chat не ответил — модалка открывается с ним же)."
  []
  [:div {:id "assistant-body"
         :class "flex flex-col h-full bg-base-200"}
   [:div {:class "flex-1 flex items-center justify-center text-sm text-base-content/50"}
    (i18n/t :ai/assistant-loading)]])

(defn modal
  "Bottom-sheet модалка ассистента: нативный <dialog>, закрытие по крестику,
   drag-handle, клику по скриму (modal-backdrop) или Esc. Контент — в
   #assistant-body, подменяется фрагментом из GET /ai/chat."
  []
  [:dialog {:id "assistant-modal"
            :data-testid "assistant-modal"
            :class "modal modal-bottom"}
   [:div {:class "modal-box h-[92dvh] w-full max-w-lg p-0 bg-base-200 overflow-hidden"}
    (loading-body)]
   [:form {:method "dialog" :class "modal-backdrop"}
    [:button (i18n/t :ai/chat-close)]]])
