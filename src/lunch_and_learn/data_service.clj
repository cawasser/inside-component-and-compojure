(ns lunch-and-learn.data-service
  (:import [org.apache.kafka.streams StreamsConfig KafkaStreams StreamsBuilder KeyValue]
           [org.apache.kafka.streams.state QueryableStoreTypes]
           [org.apache.kafka.streams.kstream ValueMapper Reducer JoinWindows ValueTransformer Transformer]

           [org.apache.kafka.clients.consumer ConsumerConfig]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord])
  (:require [jackdaw.streams :as j]
            [jackdaw.client :as jc]
            [jackdaw.serdes.edn :as jse]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]))


(def app-config {"bootstrap.servers"                     "localhost:9092"
                 StreamsConfig/APPLICATION_ID_CONFIG     "aoi-state"
                 StreamsConfig/COMMIT_INTERVAL_MS_CONFIG 500
                 ConsumerConfig/AUTO_OFFSET_RESET_CONFIG "latest"
                 "acks"                                  "all"
                 "retries"                               "0"
                 "cache.max.bytes.buffering"             "0"})


(defn topic-config [name]
  {:topic-name         name
   :partition-count    2
   :replication-factor 1
   :key-serde          (jse/serde)
   :value-serde        (jse/serde)})


(defn get-one-aoi-local [streams store aoi]
  (try
    (if-let [ret (-> streams
                   (.store store (QueryableStoreTypes/keyValueStore))
                   (.get {:aoi aoi}))]
      {:id aoi :data-set ret}
      {})
    (catch Exception _ {})))


(defn get-all-aois-local [streams store]
  (try
    (let [s (-> streams
              (.store store (QueryableStoreTypes/keyValueStore)))
          k (.all s)]
      (into []
        (map (fn [x]
               {:id (:aoi (.key x)) :data-set (.value x)})
          (iterator-seq k))))
    (catch Exception _ [])))


(defn get-all-aois [stream host port store]
  (let [meta-vec (-> stream
                   (.allMetadataForStore store))]
    (->>
      (mapcat #(if (not (and (= host (-> % .host)) (= port (-> % .port))))
                 (-> (client/get
                       (str "http://" (-> % .host) ":" (-> % .port) "/aois-local")
                       {:throw-exceptions false
                        :accept           :edn})
                   :body
                   clojure.edn/read-string)
                 (get-all-aois-local stream store))
        meta-vec)
      (into []))))


(defn get-one-aoi [streams store host port aoi]
  (let [meta-key    (.queryMetadataForKey
                      streams
                      store
                      {:aoi aoi}
                      (jse/serializer))
        remote-host (-> meta-key .activeHost .host)
        remote-port (-> meta-key .activeHost .port)]
    (if (not (and
               (= host remote-host)
               (= port remote-port)))
      (-> (client/get
            (str "http://" remote-host ":" remote-port "/aoi/" aoi)
            {:throw-exceptions false
             :accept           :edn})
        :body
        clojure.edn/read-string)
      (get-one-aoi-local streams store aoi))))


(defn pipeline [builder in-topic out-topic]
  (let [aoi-events-stream (j/kstream builder (topic-config in-topic))]
    (-> aoi-events-stream
      (j/filter (fn [[k v]]
                  (#{"aoi-added" "aoi-updated" "aoi-deleted"} (:event-type v))))
      (j/group-by-key)
      (j/aggregate (constantly #{})
        (fn [_ [_ event]]
          (log/info "processing" in-topic event)
          (cond
            (= "aoi-added" (:event-type event)) (:aoi-needs event)
            (= "aoi-removed" (:event-type event)) (:aoi-needs event)
            (= "aoi-deleted" (:event-type event)) #{}))
        (topic-config out-topic))
      (j/to-kstream)
      (j/to (topic-config out-topic))))
  builder)


(defn start! [config]
  (log/info "start! (config)" config)
  (let [full-config     (merge app-config {StreamsConfig/STATE_DIR_CONFIG          (str "./tmp/" (:host config) "/" (:port config))
                                           StreamsConfig/APPLICATION_SERVER_CONFIG (str (:host config) ":" (:port config))})
        _               (println "full-config:" full-config)
        streams-builder (j/streams-builder)
        topo            (pipeline streams-builder (:in-topic config) (:out-topic config))
        _               (println (-> topo j/streams-builder* .build .describe .toString))
        kafka-streams   (j/kafka-streams topo full-config)]
    (j/start kafka-streams)
    kafka-streams))


(defrecord KafkaTopology [config topology]
  component/Lifecycle
  (start [component]
    (println "KafkaTopology/start:" config topology component)
    (let [topology (start! config)]
      (assoc component :topology topology
        :in-topic (:in-topic config)
        :out-topic (:out-topic config))))

  (stop [component]
    (j/close topology)
    (assoc component :topology topology)))


(defn new-kafka-topology
  [config]
  (println "new-kafka-topology:" config)
  (map->KafkaTopology config))


; debug starting the Kafka Stream topology
(comment
  (do
    (def config {:host "localhost" :port 5050 :in-topic "aois" :out-topic "aoi-state"})
    (def full-config (merge app-config {StreamsConfig/STATE_DIR_CONFIG          (str "./tmp/" (:host config) "/" (:port config))
                                        StreamsConfig/APPLICATION_SERVER_CONFIG (str (:host config) ":" (:port config))}))
    (def streams-builder (j/streams-builder))
    (def topo (pipeline streams-builder (:in-topic config) (:out-topic config)))
    (println (-> topo j/streams-builder* .build .describe .toString))
    (def kafka-streams (j/kafka-streams topo app-config)))


  (map->KafkaTopology {:config config})

  (j/start kafka-streams)

  (j/close kafka-streams)

  (get-one-aoi kafka-streams (:out-topic config) "localhost" 5050 "alpha")
  (get-one-aoi kafka-streams (:out-topic config) "localhost" 5050 "bravo")
  (get-one-aoi kafka-streams (:out-topic config) "localhost" 5050 "delta")
  (get-all-aois kafka-streams (:out-topic config "localhost" 5050))

  ())


; send some events to "aois" topic using Kafka
(comment
  (do
    (require '[jackdaw.admin :as ja])

    ; pulled THIS code from https://github.com/DaveWM/kafka-streams-the-clojure-way
    ;
    ; we only need to do this ONCE, just to get the input event-streams built correctly.
    ;
    ; NOTE: I think we need to use the admin-client to get multiple partitions, it seems that just using jc/produce!
    ;       ignores the :partition-count
    ;
    (def admin-client (ja/->AdminClient app-config))
    (ja/create-topics! admin-client [(topic-config "aois") (topic-config "aoi-state")]))

  (do
    (require '[com.stuartsierra.component.repl :refer [reset set-init start stop system]])

    (defn produce-one
      ([topic k v]
       (with-open [producer (jc/producer app-config (topic-config topic))]
         @(jc/produce! producer (topic-config topic) k v)))))


  (get-all-aois (:topology (:topology system))
    (-> system :topology :config :host)
    (-> system :topology :config :port)
    (:out-topic (:topology system)))

  (produce-one "aois"
    {:aoi "alpha"}
    {:event-type :aoi-added
     :aoi-needs  #{[7 7 "x" 0]}
     :aoi        "alpha"})

  (produce-one "aois"
    {:aoi "bravo"}
    {:event-type :aoi-added
     :aoi-needs  #{[9 9 "ka" 0] [9 9 "ka" 2] [9 9 "ka" 1]}
     :aoi        "bravo"})

  (produce-one "aois"
    {:aoi "alpha"}
    {:event-type :aoi-added
     :aoi-needs  #{[7 7 "x" 0] [7 7 "x" 1] [7 7 "x" 2] [7 7 "x" 3]}
     :aoi        "alpha"})
  (produce-one "aois"
    {:aoi "charlie"}
    {:event-type :aoi-added
     :aoi-needs  #{[7 7 "ka" 4] [7 7 "ka" 5] [7 7 "ka" 6] [7 7 "ka" 7]}
     :aoi        "charlie"})

  (:topology system)

  ())


; fix the get-aois-local (so it returns schema-valid results
(comment
  (do
    (require '[com.stuartsierra.component.repl :refer [system]])

    (def streams (:topology (:topology system)))
    (def store (:out-topic (:topology system)))
    (def aoi {:aoi "alpha"})

    (def s (-> streams
             (.store store (QueryableStoreTypes/keyValueStore))))
    (def k (.all s)))

  (let [s (-> streams
            (.store store (QueryableStoreTypes/keyValueStore)))
        k (.all s)]
    (into []
      (map (fn [x]
             {:id (:aoi (.key x)) :data-set (.value x)})
        (iterator-seq k))))

  (get-all-aois-local streams store)

  ())

; learn how to use kafka streams metadata (get all the aois)
(comment
  (do
    (require '[com.stuartsierra.component.repl :refer [system]])

    (def stream (:topology (:topology system)))
    (def store (:out-topic (:topology system)))
    (def host (:host (:config (:topology system))))
    (def port (:port (:config (:topology system)))))

  (get-all-aois stream host port store)

  ; get any aois that we "host" locally
  (def locals (get-all-aois-local
                (:topology (:topology system))
                (:out-topic (:topology system))))

  (def meta-vec (-> (:topology (:topology system))
                  (.allMetadataForStore (:out-topic (:topology system)))))
  (first meta-vec)
  (-> meta-vec first .host)
  (-> meta-vec first .port)
  (:host (:config (:topology system)))
  (:port (:config (:topology system)))

  ; call the remote system and get its data
  (-> (client/get
        (str "http://localhost:5051/aois")
        {:throw-exceptions false
         :accept           :edn})
    :body
    clojure.edn/read-string)
  (-> (client/get
        (str "http://localhost:5055/aois")
        {:throw-exceptions false
         :accept           :edn})
    :body
    clojure.edn/read-string)
  (-> (client/get
        (str "http://" (-> meta-vec first .host) ":" (-> meta-vec first .port) "/aois")
        {:throw-exceptions false
         :accept           :edn})
    :body
    clojure.edn/read-string)

  ; what do we do if WE (our config) are provided? DON'T CALL! (infinite loop)
  (let [topo     (:topology system)
        meta-vec (-> (:topology topo)
                   (.allMetadataForStore (:out-topic topo)))
        thisHost (:host (:config topo))
        thisPort (:port (:config topo))]
    (-> (mapcat #(if (not (and (= thisHost (-> % .host)) (= thisPort (-> % .port))))
                   (do
                     (println "remote")
                     (-> (client/get
                           (str "http://" (-> % .host) ":" (-> % .port) "/aois")
                           {:throw-exceptions false
                            :accept           :edn})
                       :body
                       clojure.edn/read-string))
                   (do
                     (println "local")
                     (get-all-aois-local
                       (:topology (:topology system))
                       (:out-topic (:topology system)))))
          meta-vec)
      (into [])))


  ())

; now we can work out the "get-one-aoi" call logic
(comment
  (do
    (require '[com.stuartsierra.component.repl :refer [system]])

    (def streams (:topology (:topology system)))
    (def store (:out-topic (:topology system)))
    (def aoi "alpha")
    (def host (:host (:config (:topology system))))
    (def port (:port (:config (:topology system)))))

  (get-one-aoi-local
    (:topology (:topology system))
    (:out-topic (:topology system)) "alpha")


  (def meta-key (.queryMetadataForKey
                  (:topology (:topology system))
                  (:out-topic (:topology system))
                  aoi
                  (jse/serializer)))

  (let [meta-key (.queryMetadataForKey
                   (:topology (:topology system))
                   (:out-topic (:topology system))
                   {:aoi aoi}
                   (jse/serializer))]
    (if (not (and
               (= host (-> meta-key .activeHost .host))
               (= port (-> meta-key .activeHost .port))))
      (-> (client/get
            (str "http://" (-> meta-key .host) ":" (-> meta-key .port) "/aoi/" aoi)
            {:throw-exceptions false
             :accept           :edn})
        :body
        clojure.edn/read-string)
      (get-one-aoi-local streams store aoi)))



  ())

; make sure we get ALL the remotes PLUS anything local
(comment
  (do
    (require '[com.stuartsierra.component.repl :refer [system]])

    (def topo (:topology system))
    (def meta-vec (-> (:topology topo)
                    (.allMetadataForStore (:out-topic topo))))
    (def thisHost (:host (:config topo)))
    (def thisPort (:port (:config topo)))
    (def stream (:topology (:topology system)))
    (def store (:out-topic (:topology system)))
    (def host (:host (:config (:topology system))))
    (def port (:port (:config (:topology system)))))

  (get-all-aois-local stream store)

  (-> (client/get
        (str "http://localhost:5050/aois-local")
        {:throw-exceptions false
         :accept           :edn})
    :body
    clojure.edn/read-string)

  (-> (client/get
        (str "http://localhost:5051/aois-local")
        {:throw-exceptions false
         :accept           :edn})
    :body
    clojure.edn/read-string)

  (mapcat #(if (not (and (= host (-> % .host)) (= port (-> % .port))))
             [:remote]
             [:local])
    meta-vec)

  (->> (mapcat #(if (not (and (= host (-> % .host)) (= port (-> % .port))))
                  (-> (client/get
                        (str "http://" (-> % .host) ":" (-> % .port) "/aois-local")
                        {:throw-exceptions false
                         :accept           :edn})
                    :body
                    clojure.edn/read-string)
                  (get-all-aois-local stream store))
         meta-vec)
    (into []))


  (let [meta-vec (-> stream (.allMetadataForStore store))]
    (->>
      (mapcat #(if (not (and (= host (-> % .host)) (= port (-> % .port))))
                 [:remote]
                 [:local])
        meta-vec)
      (into [])))

  ())


(comment
  ; On Linux or Windows(?)
  ; docker run -d --rm --net=host landoop/fast-data-dev

  ; On Mac
  ; docker run -d --rm -p 2181:2181 -p 3030:3030 -p 8081-8083:8081-8083 -p 9581-9585:9581-9585 -p 9092:9092 -e ADV_HOST=localhost landoop/fast-data-dev:latest

  (do
    (require '[jackdaw.admin :as ja])
    (require '[clojure.edn :as edn])
    (def path "./resources/public/")
    (def store "aois"))

  (do
    (def admin-client (ja/->AdminClient app-config))
    (ja/create-topics! admin-client [(topic-config "aois") (topic-config "aoi-state")]))

  (do
    (defn produce-one
      ([topic k v]
       (with-open [producer (jc/producer app-config (topic-config topic))]
         @(jc/produce! producer (topic-config topic) k v)))))

  (->> (str path "default-data/default-aois.edn")
    slurp
    edn/read-string
    (map (fn [[k v]]
           (produce-one store k v))))

  ; just send one update
  (produce-one store
    {:aoi "alpha-hd"}
    {:event-type "aoi-updated",
     :aoi "alpha-hd",
     :aoi-needs #{[7 7 "hidef-image" 0] [7 6 "hidef-image" 1]
                  [7 5 "hidef-image" 3] [7 6 "hidef-image" 2]}})

  (produce-one store
    {:aoi "charlie-hd"}
    {:event-type "aoi-added",
     :aoi "charlie-hd",
     :aoi-needs #{[7 7 "hidef-image" 0] [7 6 "hidef-image" 1]
                  [7 5 "hidef-image" 3] [7 6 "hidef-image" 2]}})
  (produce-one store
    {:aoi "charlie-hd"}
    {:event-type "aoi-updated",
     :aoi "charlie-hd",
     :aoi-needs #{[4 4 "hidef-image" 0] [4 5 "hidef-image" 1]}})




  (produce-one store
    {:aoi "tropical-hd"}
    {:event-type "aoi-added",
     :aoi "tropical-hd",
     :aoi-needs #{[7 7 "hidef-image" 0] [7 6 "hidef-image" 1]
                  [7 5 "hidef-image" 3] [7 6 "hidef-image" 2]}})

  ())