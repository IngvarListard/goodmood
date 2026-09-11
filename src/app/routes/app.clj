(ns app.routes.app
  (:require [reitit.ring :as ring]
            [reitit.ring.coercion :as rc]
            [reitit.coercion.malli :as malli]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.params :as params]
            [ring.middleware.resource :as resource]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.not-modified :as not-modified]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as session.cookie]
            [ring.middleware.anti-forgery :as anti-forgery]
            [malli.core :as mc]
            [malli.error :as me]
            [malli.transform :as mt]
            [ring.util.response :as response]
            [app.routes.html :refer [html-response]]
            [hiccup2.core :refer [html]]
            [app.middleware :as mw]
            [app.db.push :as push-db]
            [app.domains.ai :as ai-domains]
            [app.domains.entries :as domains]
            [app.domains.insights :as insights-domains]
            [app.domains.medications :as med-domains]
            [app.domains.notification-settings :as notif-domains]
            [app.domains.push :as push-domains]
            [app.routes.ai :as ai-routes]
            [app.routes.auth :as auth]
            [app.routes.check-in :as check-in]
            [app.routes.entries :as entries]
            [app.routes.feed :as feed]
            [app.routes.insights :as insights]
            [app.routes.medications :as med-routes]
            [app.routes.notifications :as notifications]
            [app.routes.push :as push-routes]
            [app.routes.state-periods :as period-routes]
            [app.views.entries :as views]
            [app.views.settings :as settings]))

(defn health-check
  "Вернуть OK для проверок health-check балансировщика."
  [_]
  (response/response "OK"))

(defn- blank->nil
  "Вернуть nil для пустой строки, иначе исходное значение."
  [v]
  (if (and (string? v) (clojure.string/blank? v))
    nil
    v))

(defn- blank->nil-double
  [v]
  (if (and (string? v) (clojure.string/blank? v))
    nil
    ((mc/decoder :double nil mt/string-transformer) v)))

(defn- blank->nil-int
  [v]
  (if (and (string? v) (clojure.string/blank? v))
    nil
    ((mc/decoder :int nil mt/string-transformer) v)))

(def ^:private blank-aware-body-transformer
  (mt/transformer mt/string-transformer
                  {:decoders {:string blank->nil
                              :double blank->nil-double
                              :int blank->nil-int}}))

(def ^:private blank-aware-body-provider
  (reify malli/TransformationProvider
    (-transformer [_ options]
      (mt/transformer
       (when (:strip-extra-keys options) (mt/strip-extra-keys-transformer))
       blank-aware-body-transformer
       (when (:default-values options) (mt/default-value-transformer))))))

(def ^:private app-coercion
  (malli/create
   {:transformers
    (assoc (:transformers malli/coercion)
           :body {:default malli/string-transformer-provider
                  :formats {"application/json" blank-aware-body-provider}})}))

(defn- flatten-errors
  [errors]
  (mapcat (fn [[k msgs]]
            (map (fn [m] (str (if (keyword? k) (name k) (str k)) ": " m)) msgs))
          errors))

(defn- coercion-error-middleware
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (= :reitit.coercion/request-coercion (:type data))
            (let [errors (me/humanize (select-keys data [:errors :value]) {:wrap :message})]
              (if (entries/htmx-request? request)
                {:status 400
                 :headers {"Content-Type" "text/html; charset=utf-8"}
                 :body (str (html (views/error-fragment (flatten-errors errors))))}
                {:status 400
                 :body {:errors errors}}))
            (throw e)))))))

(defn- settings-page-handler
  [ds request]
  (let [uid (get-in request [:identity :id])
        notif-settings (notif-domains/get-settings ds uid)
        ai-settings (ai-domains/get-settings ds uid)
        push-state {:configured? (push-domains/vapid-configured?)
                    :subscribed? (boolean (seq (push-db/get-subscriptions ds uid)))}]
    (html-response 200 (settings/page request notif-settings ai-settings push-state))))

(defn- anti-forgery-error-handler
  [_]
  {:status 403
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body "Invalid anti-forgery token"})

(defn- root-handler
  "Для аутентифицированного пользователя — редирект на /feed;
   для health-check (curl) — OK."
  [request]
  (if (:identity request)
    (response/redirect "/feed")
    {:status 200
     :headers {"Content-Type" "text/plain; charset=utf-8"}
     :body "OK"}))

(defn- router
  [ds]
  (ring/router
   [["/" {:get {:handler root-handler}
          :auth/public true}]
    ["/login" {:get {:handler (partial auth/login-page-handler ds)}
               :post {:handler (partial auth/login-post-handler ds)}
               :auth/public true}]
    ["/logout" {:post {:handler auth/logout-post-handler}
                :auth/public true}]
    ["/locale" {:post {:handler auth/locale-post-handler}
                :auth/public true}]
    ["/theme" {:post {:handler auth/theme-post-handler}
               :auth/public true}]
    ["/feed" {:get {:handler (partial feed/page ds)}}]
    ["/feed/radar" {:get {:handler (partial feed/radar ds)}}]
    ["/check-in" {:get {:handler (partial check-in/page ds)}}]
    ["/settings" {:get {:handler (partial settings-page-handler ds)}}]
    ["/entries"
     {:post {:parameters {:body domains/create-entry-schema}
             :handler (partial entries/create-entry ds)}
      :get {:handler (partial entries/list-page ds)}}]
    ["/entries/:id"
     {:get {:handler (partial entries/show-page ds)}
      :post {:parameters {:body domains/update-entry-schema}
             :handler (partial entries/update-entry ds)}
      :delete {:handler (partial entries/delete-entry ds)}
      :conflicting true}]
    ["/medications"
     {:post {:parameters {:body med-domains/medication-schema}
             :handler (partial med-routes/create-medication ds)}
      :get {:handler (partial med-routes/page ds)}}]
    ["/medications/new"
     {:get {:handler (partial med-routes/new-modal ds)}
      :conflicting true}]
    ["/medications/:id"
     {:post {:parameters {:body med-domains/medication-schema}
             :handler (partial med-routes/update-medication ds)}
      :conflicting true}]
    ["/medications/:id/edit"
     {:get {:handler (partial med-routes/edit-modal ds)}}]
    ["/medications/:id/deactivate"
     {:post {:handler (partial med-routes/deactivate ds)}}]
    ["/medications/:id/activate"
     {:post {:handler (partial med-routes/activate ds)}}]
    ["/medications/:id/log"
     {:post {:parameters {:body med-domains/log-schema}
             :handler (partial med-routes/log-intake ds)}}]
    ["/insights"
     {:post {:parameters {:body insights-domains/create-insight-schema}
             :handler (partial insights/create ds)}
      :get {:handler (partial insights/page ds)}}]
    ["/insights/new"
     {:get {:handler (partial insights/new-page ds)}
      :conflicting true}]
    ["/insights/advice-item"
     {:get {:handler insights/advice-item}
      :conflicting true}]
    ["/insights/:id"
     {:get {:handler (partial insights/show ds)}
      :delete {:handler (partial insights/delete ds)}
      :conflicting true}]
    ["/insights/:id/edit-context"
     {:get {:handler (partial insights/edit-context ds)}}]
    ["/insights/:id/advice-items"
     {:get {:handler (partial insights/advice-items ds)}}]
    ["/insights/:id/context"
     {:post {:handler (partial insights/update-context ds)}}]
    ["/insights/:id/edit-advice"
     {:get {:handler (partial insights/edit-advice ds)}}]
    ["/insights/:id/advice"
     {:post {:handler (partial insights/update-advice ds)}}]
    ["/insights/:id/edit-identity"
     {:get {:handler (partial insights/edit-identity ds)}}]
    ["/insights/:id/identity"
     {:post {:handler (partial insights/update-identity ds)}}]
    ["/feed/pending-insight"
     {:get {:handler (partial notifications/pending-insight ds)}}]
    ["/notifications/summary-dismiss"
     {:post {:handler (partial notifications/summary-dismiss ds)}}]
    ["/settings/notifications"
     {:post {:handler (partial notifications/settings-update ds)}}]
    ["/settings/ai"
     {:post {:handler (partial ai-routes/settings-update ds)}}]
    ["/push/subscribe"
     {:post {:handler (partial push-routes/subscribe ds)}}]
    ["/push/unsubscribe"
     {:post {:handler (partial push-routes/unsubscribe ds)}}]
    ["/ai/correlations"
     {:post {:handler (partial ai-routes/correlations-fragment ds)}}]
    ["/ai/label"
     {:post {:handler (partial ai-routes/state-label-fragment ds)}}]
    ["/ai/label/apply"
     {:post {:handler (partial ai-routes/apply-label ds)}}]
    ["/ai/advice"
     {:post {:handler (partial ai-routes/advice-fragment ds)}}]
    ["/ai/findings/:id/feedback"
     {:post {:handler (partial ai-routes/feedback ds)}}]
    ["/ai/chat"
     {:get {:handler (partial ai-routes/chat-panel ds)}
      :post {:handler (partial ai-routes/chat-send ds)}}]
    ["/ai/chat/new"
     {:post {:handler (partial ai-routes/chat-new ds)}}]
    ["/ai/chat/disclaimer"
     {:post {:handler (partial ai-routes/chat-disclaimer ds)}}]
    ["/ai/novel-advice"
     {:post {:handler (partial ai-routes/novel-advice-fragment ds)}}]
    ["/ai/episode-warning"
     {:get {:handler (partial ai-routes/episode-warning-fragment ds)}}]
    ["/ai/episode-warning/disable"
     {:post {:handler (partial ai-routes/episode-warning-disable ds)}}]
    ["/ai/episode-warning/:id/feedback"
     {:post {:handler (partial ai-routes/episode-warning-feedback ds)}}]
    ["/ai/episode-warning/:id/dismiss"
     {:post {:handler (partial ai-routes/episode-warning-dismiss ds)}}]
    ["/periods/start"
     {:post {:handler (partial period-routes/start ds)}}]
    ["/periods/:id/end"
     {:post {:handler (partial period-routes/end ds)}}]]
   {:data {:coercion app-coercion
           :middleware [rc/coerce-request-middleware
                        rc/coerce-response-middleware]}}))

(defn session-config
  "Сформировать конфигурацию cookie-хранилища сессий Ring из секретного ключа."
  [session-secret]
  {:store (session.cookie/cookie-store {:key (mw/secret-key session-secret)})
   :cookie-name "gm-session"
   :cookie-attrs {:http-only true :same-site :lax :max-age 7776000}})

(defn ->app
  "Создать Ring-приложение: роутер, стэк middleware и конфигурацию сессий."
  [ds session-secret]
  (let [router (router ds)]
    (-> (ring/ring-handler router (ring/create-default-handler))
        coercion-error-middleware
        (mw/require-auth router)
        mw/wrap-identity
        mw/wrap-locale
        mw/wrap-theme
        (anti-forgery/wrap-anti-forgery
         {:read-token mw/read-csrf-token
          :error-handler anti-forgery-error-handler})
        (session/wrap-session (session-config session-secret))
        muuntaja/wrap-format
        params/wrap-params
        mw/wrap-request-log
        ;; Локальные статики: снаружи require-auth — без сессии, как CDN.
        ;; resources/public/js/radar.js -> /js/radar.js
        (resource/wrap-resource "public")
        content-type/wrap-content-type
        not-modified/wrap-not-modified)))