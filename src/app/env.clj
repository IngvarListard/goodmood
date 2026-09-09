(ns app.env
  "Единая точка доступа к конфигурации через переменные окружения.
   Для локальной разработки значения можно задавать в `.env`-файле в корне
   проекта (KEY=VALUE, комментарии через #). Реальный env процесса всегда
   сильнее файла; отсутствие `.env` ошибкой не является (контейнер получает
   переменные через compose env_file и файл ему не нужен)."
  (:require [clojure.java.io :as io]
            [lambdaisland.dotenv :as dotenv]))

(def ^:private dotenv-path ".env")

(def ^:private cached
  "Ленивый разбор `.env` (один раз за процесс)."
  (delay
    (let [f (io/file dotenv-path)]
      (when (.exists f)
        (dotenv/parse-dotenv (slurp f))))))

(defn env
  "Значение переменной окружения по имени (строкой): реальный env имеет
   приоритет над `.env`; иначе `default` (по умолчанию nil)."
  ([k]
   (or (System/getenv k)
       (clojure.core/get @cached k)))
  ([k default]
   (or (env k) default)))

(defn all
  "Все переменные окружения одной map (`.env` слит с реальным env,
   реальный env сильнее). Для функций, принимающих env-мапу целиком."
  []
  (merge @cached (System/getenv)))
