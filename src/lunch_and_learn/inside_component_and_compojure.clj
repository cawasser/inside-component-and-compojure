(ns lunch-and-learn.inside-component-and-compojure
  (:require [com.stuartsierra.component :as component]
            [com.stuartsierra.component.repl :refer [reset set-init start stop system]]
            [lunch-and-learn.jetty-server :as server]
            [lunch-and-learn.data-service :as topo])
  (:gen-class))


; working with Component and Compojure at the REPL

(defn new-system [_]
  (component/system-map
    :server (component/using (server/map->HTTPServer {:port 5050}) [:topology])
    :topology (topo/new-kafka-topology "aois" "aoi-state")))


; run the server from the REPL
(comment
  ; init the system so we can start and stop it from the REPL
  (set-init new-system)

  (start)

  (keys system)
  (:server system)
  (:topology system)

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
  (tap> [1 2 3 4 {:a 1 :b 2}])

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

; play with Reveal
(comment

  "https://vlaaad.github.io/reveal/"

  (tap> "hello")
  (tap> [1 2 3 4 {:a 1 :b 2}])

  (all-ns)

  (System/getProperty "vlaaad.reveal.prefs")

  ; clear the reveal window
  {:vlaaad.reveal/command '(clear-output)}

  (def an-atom (atom {:a 1}))

  (reset! an-atom {:b 3})
  (swap! an-atom assoc :a 10)


  {:vlaaad.reveal/command '(open-view {:fx/type action-view
                                       :action :vlaaad.reveal.action/view:table
                                       :value v})
   :env {'v (ns-publics *ns*)}}

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



; can we use add-libs in THIS project?
(comment
  (require '[clojure.tools.deps.alpha.repl :refer [add-libs]])

  (add-libs '{ring/ring {:mvn/version "1.9.4"}})

  ())

; NEXT STEPS
;
; middleware
;       transit
;







(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (greet {:name (first args)}))
