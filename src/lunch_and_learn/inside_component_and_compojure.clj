(ns lunch-and-learn.inside-component-and-compojure
  (:require [com.stuartsierra.component :as component]
            [com.stuartsierra.component.repl :refer [reset set-init start stop system]]
            [lunch-and-learn.jetty-server :as server])
  (:gen-class))


; working with Component and Compojure at the REPL

(defn new-system [_]
  (component/system-map
    :server (server/new-http-server 5050)))


; run the serve form the REPL
(comment
  ; init the system so we can start and stop it from the REPL
  (set-init new-system)

  (start)

  (keys system)
  (:server system)

  (stop)

  (reset)



  ())


; play with :inspect/portal-cli (or :inspect/reveal)
(comment
  (do
    (require '[portal.api :as p])
    (p/open)
    (add-tap #'p/submit))

  (tap> "hello")
  ; Clear all values
  (p/clear)
  ; Remove portal from tap> targetset
  (remove-tap #'p/submit)

  ; init the system so we can start and stop it from the REPL
  (set-init new-system)

  (start)

  (keys system)
  (:server system)

  (stop)

  (reset)

  ())


; play wth kaocha (test-runner) from the REPL
(comment
  (do
    (require '[kaocha.repl :as k]))


  (k/run :unit)

  (k/test-plan)
  (k/config)

  ())


; play with jetty and handle directly to see if (tap>) is working
(comment

  (do
    (require '[ring.adapter.jetty :refer [run-jetty]])
    (def server (atom nil))

    (defn handler [req]
      (tap> req)
      {:status 200 :body "hello, world"})

    (defn go []
      (println "starting server on http://localhost:3000")
      (reset! server (run-jetty #'handler {:port 3000 :join? false}))))

  (go)

  (ns-publics 'ring.adapter.jetty)

  (.stop @server)

  ())


; NEXT STEPS
;
; middleware
;







(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (greet {:name (first args)}))
