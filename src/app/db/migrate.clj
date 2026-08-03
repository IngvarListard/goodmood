(ns app.db.migrate
  (:require [migratus.core :as migratus]))

(defn config
  [db]
  {:store :database
   :migration-dir "migrations"
   :db {:datasource db}})

(defn migrate!
  [db]
  (migratus/migrate (config db)))
