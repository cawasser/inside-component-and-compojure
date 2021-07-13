(ns lunch-and-learn.inside-component-and-compojure
  (:require [com.stuartsierra.component :as component]
            [com.stuartsierra.component.repl :refer [reset set-init start stop system]]
            [lunch-and-learn.jetty-server :as server])
  (:gen-class))


; working with Component and Compojure at the REPL

(defn new-system [_]
  (component/system-map
    :server (server/new-http-server 5000)))

(comment
  ; init the system so we can start and stop it from the REPL
  (set-init new-system)

  (start)

  (keys system)
  (:server system)

  (stop)

  (reset)

  ())








(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (greet {:name (first args)}))
