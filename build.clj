(ns build
  "Сборка дистрибутива: clj -T:build uberjar → runnable uberjar в target/."
  (:require [clojure.tools.build.api :as b]))

(def ^:private class-dir "target/classes")
(def ^:private basis (b/create-basis {:project "deps.edn"}))
(def ^:private uber-file
  (str "target/goodmood-" (b/git-count-revs {:root "."}) ".jar"))

(defn clean
  "Удалить target/."
  [_]
  (b/delete {:path "target"})
  (println "Cleaned target/"))

(defn uberjar
  "Собрать runnable uberjar (main app.core).
   Ресурсы кладутся целиком (миграции, i18n, иконки, public), кроме
   локальных dev-файлов БД goodmood.db* — они не должны попадать в образ."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir
               :ignores #{#"goodmood\.db.*"}})
  (b/compile-clj {:basis basis :class-dir class-dir :ns-compile '[app.core]})
  (b/uber {:class-dir class-dir :uber-file uber-file :basis basis :main 'app.core})
  (println "Built" uber-file))
