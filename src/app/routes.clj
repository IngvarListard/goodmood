(ns app.routes
  (:require [reitit.ring :as ring]
            [ring.util.response :as response]))

(defn health-check [_]
  (response/response "OK"))

(def routes
  ["/" health-check])

(def app
  (ring/ring-handler
   (ring/router routes)))
