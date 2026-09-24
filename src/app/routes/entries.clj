(ns app.routes.entries
  (:require [app.domains.entries :as entries]
            [app.routes.html :refer [html-response]]
            [app.views.entries :as views]))

(defn htmx-request?
  "Вернуть true если запрос является HTMX AJAX-запросом."
  [request]
  (= "true" (get-in request [:headers "hx-request"])))

(defn create-entry
  "Создать новую запись настроения для аутентифицированного пользователя.
   Возвращает HTML-фрагмент для HTMX-запросов или JSON для API."
  [ds request]
  (let [{:keys [date activity effect mood_score energy anxiety focus aggression
                sleep_hours sleep_minutes note template]}
        (get-in request [:parameters :body])
        user-id (get-in request [:identity :id])
        entry (entries/create-entry ds user-id
                                    {:date date
                                     :activity activity
                                     :effect effect
                                     :mood_score mood_score
                                     :energy energy
                                     :anxiety anxiety
                                     :focus focus
                                     :aggression aggression
                                     :sleep_hours sleep_hours
                                     :sleep_minutes sleep_minutes
                                     :note note
                                     :template template})]
    (if (htmx-request? request)
      (html-response 201 (views/item entry))
      {:status 201
       :body entry})))

(defn- user-id
  [request]
  (get-in request [:identity :id]))

(defn- path-id
  [request]
  (some-> (get-in request [:path-params :id])
          parse-long))

(defn list-page
  "GET /entries — полный список записей пользователя (вместо прежнего
   редиректа на /feed)."
  [ds request]
  (html-response 200 (views/list-page request (entries/list-entries ds (user-id request)))))

(defn show-page
  "GET /entries/:id — карточка записи с edit/delete. Чужая запись → 404."
  [ds request]
  (let [entry (entries/get-entry ds (user-id request) (path-id request))]
    (if entry
      (html-response 200 (views/show-page request entry))
      {:status 404
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body "Not found"})))

(defn update-entry
  "POST /entries/:id — обновить переданные поля записи. HTMX → OOB-свап
   обновлённой карточки (ошибки валидации прилетают в #entry-form-error);
   API → JSON обновлённой записи. Чужая запись → 404."
  [ds request]
  (let [id (path-id request)
        params (get-in request [:parameters :body])
        updated (entries/update-entry ds (user-id request) id params)]
    (cond
      (nil? updated)
      {:status 404 :body "Not found"}

      (htmx-request? request)
      (html-response 200 (views/card-oob (:anti-forgery-token request) updated))

      :else
      {:status 200 :body updated})))

(defn delete-entry
  "DELETE /entries/:id — жёсткое удаление. HTMX с ?from=feed → 200: карточку
   убирает hx-swap=delete, а если записей за день не осталось — тело несёт
   OOB-удаление секции дня (design D3/D4). Прочий HTMX → HX-Redirect /entries;
   non-htmx → 204. Чужая/несуществующая запись → 404 (не удалена)."
  [ds request]
  (let [uid (user-id request)
        deleted (entries/delete-entry ds uid (path-id request))]
    (cond
      (nil? deleted)
      {:status 404 :body "Not found"}

      (and (htmx-request? request)
           (= "feed" (get-in request [:query-params "from"])))
      (let [date (:date deleted)
            remaining (filter #(= date (:date %)) (entries/list-entries ds uid))]
        (html-response
         200
         (if (empty? remaining)
           [:div {:id (str "feed-day-" date) :hx-swap-oob "delete"}]
           "")))

      (htmx-request? request)
      {:status 200
       :headers {"HX-Redirect" "/entries"}
       :body ""}

      :else
      {:status 204 :body ""})))