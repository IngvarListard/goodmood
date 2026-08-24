(ns app.routes.feed
  (:require [app.domains.ai :as ai]
            [app.domains.entries :as entries]
            [app.domains.insights :as insights]
            [app.domains.notification-settings :as notif-domains]
            [app.domains.state-periods :as periods]
            [app.views.feed :as views]
            [clojure.string :as str]
            [hiccup2.core :refer [html]]))

(defn- html-response
  [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (str (html body))})

(defn- ai-findings
  "Собрать AI-находки для /feed с учётом per-function opt-out.
   Возвращает map {:csrf :correlations :label :advice} или nil (AI отключён).
   label — вектор находок type=label (view берёт первый)."
  [ds user-id csrf-token]
  (when (ai/ai-enabled? ds user-id)
    (let [correlations (when (ai/correlations-enabled? ds user-id)
                         (ai/list-correlations ds user-id))
          label (when (ai/labels-enabled? ds user-id)
                  (ai/list-labels ds user-id))
          advice (when (ai/advice-enabled? ds user-id)
                   (ai/list-advice ds user-id))
          novel (when (ai/novel-advice-enabled? ds user-id)
                  (ai/list-novel-advice ds user-id))]
      (when (or (seq correlations) (seq label) (seq advice) (seq novel))
        {:csrf         csrf-token
         :correlations correlations
         :label        label
         :advice       advice
         :novel        novel}))))

(defn- raw-state-label
  "Вернуть raw state_label строки (без префикса :state/), если он задан;
   иначе вычислить rule-based из осей."
  [entry]
  (if-let [l (:state-label entry)]
    (str/replace l "state/" "")
    (-> (entries/state-label entry) name (str/replace "state/" ""))))

(defn- saved-toast-insight
  "Вернуть инсайт для toast «только что сохранил», когда /feed открыт с
   ?saved=1 (редирект из /check-in после успешного сохранения). Подбирает
   релевантный инсайт по state_label последней записи сегодня."
  [ds user-id saved?]
  (when (and saved? user-id)
    (let [today (str (java.time.LocalDate/now))
          latest (first (filter #(= today (:date %))
                                (entries/list-entries ds user-id)))]
      (when latest
        (let [state-label (raw-state-label latest)]
          (when state-label
            (insights/matching-insight ds user-id state-label)))))))

(defn- summary-showing?
  "Вернуть true, если вечернюю сводку надо показать: ≥18:00, есть записи
   сегодня и sentinel last_summary_date != сегодня (Decision 14.2, OQ6)."
  [settings today today-entries]
  (let [evening (first (filter #(= "evening" (:slot %)) settings))]
    (and (>= (Integer/parseInt (subs (str (java.time.LocalTime/now)) 0 2)) 18)
         (seq today-entries)
         (or (nil? evening)
             (not= (:last-summary-date evening) today)))))

(defn- ensure-ai-analysis!
  "Запустить фоновый анализ (корреляции/ярлык/советы) при первом обращении
   после порога. Не блокирует UI: анализ выполняется в отдельных потоках,
   результат кэшируется в ai_findings и подхватывается при следующем
   polling (Decision 5)."
  [ds user-id latest-state-label]
  (when (ai/ai-enabled? ds user-id)
    (when (and (ai/correlations-enabled? ds user-id)
               (ai/correlation-analysis-needed? ds user-id))
      (future (try (ai/analyze-correlations ds user-id)
                   (catch Exception e
                     (println "Background correlation analysis failed:" (.getMessage e))))))
    (when (and (ai/labels-enabled? ds user-id)
               (ai/label-analysis-needed? ds user-id latest-state-label))
      (future (try (ai/propose-state-label ds user-id latest-state-label)
                   (catch Exception e
                     (println "Background label proposal failed:" (.getMessage e))))))
    (when (and (ai/advice-enabled? ds user-id)
               (ai/advice-analysis-needed? ds user-id))
      (future (try (ai/generate-advice-from-insights ds user-id)
                   (catch Exception e
                     (println "Background advice generation failed:" (.getMessage e))))))
    (when (and (ai/novel-advice-enabled? ds user-id)
               (ai/novel-analysis-needed? ds user-id))
      (future (try (ai/generate-novel-advice ds user-id)
                   (catch Exception e
                     (println "Background novel advice failed:" (.getMessage e))))))
    (when (ai/episode-analysis-needed? ds user-id)
      (future (try (ai/analyze-episode-trend ds user-id)
                   (catch Exception e
                     (println "Background episode trend analysis failed:" (.getMessage e))))))))

(defn page
  "Показать ленту записей («мой день») для аутентифицированного пользователя.
   Под hero-карточкой рендерится виджет инсайтов: 1 релевантный по
   state_label последней записи сегодня, или мягкий онбординг (OQ3).
   saved — флаг ?saved=1 из редиректа /check-in (показывает toast-инсайт)."
  [ds request]
  (let [user-id (get-in request [:identity :id])
        entries (entries/list-entries ds user-id)
        today (str (java.time.LocalDate/now))
        today-entries (filter #(= today (:date %)) entries)
        latest (first today-entries)
        state-label (when latest (raw-state-label latest))
        ;; Ручной ярлык (сохранённый в entries.state_label), если задан.
        ;; AI-ярлык предлагается только при его отсутствии (spec Phase 5).
        manual-label (when latest (:state-label latest))
        insight (when state-label
                  (insights/matching-insight ds user-id state-label))
        saved? (= "1" (get-in request [:query-params "saved"]))
        toast-insight (saved-toast-insight ds user-id saved?)
        settings (notif-domains/get-settings ds user-id)
        summary? (summary-showing? settings today today-entries)
        summary-insight (when summary? insight)
        _ (ensure-ai-analysis! ds user-id manual-label)
        ai (ai-findings ds user-id (get-in request [:anti-forgery-token]))
        episode (ai/current-episode-signal ds user-id)
        active-period (periods/active-period ds user-id)
        periods-list (periods/list-periods ds user-id)
        period {:active active-period
                :list periods-list
                :csrf (get-in request [:anti-forgery-token])}]
    (html-response 200 (views/page request entries state-label insight
                                   {:toast-insight toast-insight
                                    :csrf (get-in request [:anti-forgery-token])
                                    :episode episode
                                    :summary {:show summary?
                                              :csrf (get-in request [:anti-forgery-token])
                                              :state-label state-label
                                              :insight summary-insight}
                                    :period period
                                    :ai ai}))))