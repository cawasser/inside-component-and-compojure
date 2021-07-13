(ns lunch-and-learn.jetty-server-test
  (:require [clojure.test :refer :all]
            [lunch-and-learn.jetty-server :refer [new-http-server]]
            [com.stuartsierra.component :as component]))


(def http-server (new-http-server 8080))

(deftest http-server-lifecycle
  (alter-var-root #'http-server component/start)
  (is (:server http-server) "HTTP server has been added to component")
  (is (.isStarted (:server http-server)) "HTTP server starts")
  (alter-var-root #'http-server component/stop)
  (is (nil? (:server http-server)) "HTTP server instance is removed from component"))