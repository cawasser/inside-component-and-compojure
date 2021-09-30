(ns lunch-and-learn.jetty-server
  (:require [compojure.core :refer :all]
            [com.stuartsierra.component :as component]
            ;[lunch-and-learn.router :refer [app]]
            [lunch-and-learn.api :refer [app]]
            [ring.adapter.jetty :as jetty]))


; see "https://vlaaad.github.io/reveal/"

"https://github.com/cawasser"


(defrecord HTTPServer [port server topology]
  component/Lifecycle
  (start [component]
    (println ";; Starting HTTP server")
    (tap> "starting server")
    (let [server (jetty/run-jetty (app topology) {:port port :join? false})]
      (assoc component :server server)))
  (stop [component]
    (println ";; Stopping HTTP server")
    (.stop server)
    (assoc component :server nil)))


(defn new-http-server
  [port]
  (map->HTTPServer {:port port}))