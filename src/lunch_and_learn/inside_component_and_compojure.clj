(ns lunch-and-learn.inside-component-and-compojure
  (:require [com.stuartsierra.component :as component]
            [com.stuartsierra.component.repl :refer [reset set-init start stop system]]
            [lunch-and-learn.jetty-server :as server]
            [lunch-and-learn.data-service :as topo])
  (:gen-class))

;; start kafka using one of the following:

; On Linux or Windows(?)
; docker run -d --rm --net=host landoop/fast-data-dev

; On Mac
; docker run -d --rm -p 2181:2181 -p 3030:3030 -p 8081-8083:8081-8083 -p 9581-9585:9581-9585 -p 9092:9092 -e ADV_HOST=localhost landoop/fast-data-dev:latest


; working with Component and Compojure at the REPL

; TODO: 1. Multiple partitions (test & debug)

; TODO: 5. add an nRepl we can remotely connect to

(defn new-system [args _]
  (let [topo-config (merge args {:in-topic "aois" :out-topic "aoi-state"})]
    (component/system-map
      :server (component/using (server/map->HTTPServer {:host (:host args) :port (:port args)}) [:topology])
      :topology (topo/map->KafkaTopology {:config topo-config}))))


(defn -main
  "Starts the Kafka Topology and the REST-ful endpoint webserver, hooks them together and starts processing
  AoI events into 'aoi-state'

  Assumes the PORT number to use is provided as the only CLI parameter"
  [& args]
  (component/start (new-system (zipmap [:host :port] args) {})))


; run the server from the REPL
(comment

  ; start a server without repl capability (we should add nRepl using component...)
  ; THIS instance will control the single partition...
  (-main "localhost" 5051)
  (-main "localhost" 5055)

  ; init the system so we can start and stop it from the REPL
  ;    while *this* instance has no data in local-store, and so must "cross-request"
  (set-init (partial new-system {:host "localhost" :port 5050}))
  (set-init (partial new-system {:host "localhost" :port 5051}))
  (start)

  (keys system)
  (:server system)
  (:topology system)

  (stop)

  (reset)



  ())


; how do variadics (args) work?
(comment
  (do
    (def last-args (atom {}))
    (defn var-fn [& args]
     (reset! last-args (zipmap [:host :port] args))))


  (var-fn "localhost" 5050)
  @last-args
  ; so it's a seq



  ())

; can we query the topology?
(comment

  (import '[org.apache.kafka.streams.state QueryableStoreTypes])
  (-> (:topology (:topology system))
    (.store "aoi-state" (QueryableStoreTypes/keyValueStore))
    (.get {:aoi "alpha"}))


  (topo/get-one-aoi (:topology system) "aoi-state" "alpha")

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



