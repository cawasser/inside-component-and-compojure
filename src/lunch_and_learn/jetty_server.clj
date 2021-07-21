(ns lunch-and-learn.jetty-server
  (:require [compojure.core :refer :all]
            [com.stuartsierra.component :as component]
            ;[lunch-and-learn.router :refer [app]]
            [lunch-and-learn.api :refer [app]]
            [ring.adapter.jetty :as jetty]))





(defrecord HTTPServer [port server]
  component/Lifecycle
  (start [component]
    (println ";; Starting HTTP server")
    (tap> "starting server")
    (let [server (jetty/run-jetty #'app {:port port :join? false})]
      (assoc component :server server)))
  (stop [component]
    (println ";; Stopping HTTP server")
    (.stop server)
    (assoc component :server nil)))


(defn new-http-server
  [port]
  (map->HTTPServer {:port port}))