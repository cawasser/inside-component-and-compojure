(ns lunch-and-learn.data-service
  (:import [org.apache.kafka.streams StreamsConfig KafkaStreams StreamsBuilder KeyValue]
           [org.apache.kafka.streams.state QueryableStoreTypes]
           [org.apache.kafka.streams.kstream ValueMapper Reducer JoinWindows ValueTransformer Transformer]

           [org.apache.kafka.clients.consumer ConsumerConfig]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord])
  (:require [jackdaw.streams :as j]
            [jackdaw.client :as jc]
            [jackdaw.serdes.edn :as jse]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]))


; TODO: 2. cross-request data from other members of the consumer-group (see https://docs.confluent.io/platform/current/streams/developer-guide/interactive-queries.html#querying-remote-state-stores-for-the-entire-app)
; TODO: 3. refactor so we can start multiple instances, each at a different port AND Kafka 'StreamsConfig/STATE_DIR_CONFIG'
; TODO: 4. turn into a Polylith "base" so we can re-use it

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


(defn get-one-aoi [streams store aoi]
  (-> streams
    (.store store (QueryableStoreTypes/keyValueStore))
    (.get {:aoi aoi})))


(defn get-all-aois [streams store]
  (let [s (-> streams
            (.store store (QueryableStoreTypes/keyValueStore)))
        k (.all s)]
    (into {}
      (map (fn [x]
             {(.key x) (.value x)})
        (iterator-seq k)))))



(defn pipeline [builder in-topic out-topic]
  (let [aoi-events-stream (j/kstream builder (topic-config in-topic))]
    (-> aoi-events-stream
      (j/filter (fn [[k v]]
                  (#{:aoi-added :aoi-updated :aoi-deleted} (:event-type v))))
      (j/group-by-key)
      (j/aggregate (constantly #{})
        (fn [_ [_ event]]
          (cond
            (= :aoi-added (:event-type event)) (:aoi-needs event)
            (= :aoi-removed (:event-type event)) (:aoi-needs event)
            (= :aoi-deleted (:event-type event)) #{}))
        (topic-config out-topic))
      (j/to-kstream)
      (j/to (topic-config out-topic))))
  builder)


(defn start! [config]
  (println "start!:" config)
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

  (get-one-aoi kafka-streams (:out-topic config) "alpha")
  (get-one-aoi kafka-streams (:out-topic config) "bravo")
  (get-one-aoi kafka-streams (:out-topic config) "delta")
  (get-all-aois kafka-streams (:out-topic config))

  ())


; send some events to "aois" topic using Kafka
(comment
  (do
    (require '[com.stuartsierra.component.repl :refer [reset set-init start stop system]])

    (defn produce-one
      ([topic k v]
       (with-open [producer (jc/producer app-config (topic-config topic))]
         @(jc/produce! producer (topic-config topic) k v)))))


  (get-one-aoi (:topology (:topology system))
    (:out-topic (:topology system)) "alpha")
  (get-all-aois (:topology (:topology system))
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


; learn how to use kafka streams metadata
(comment

  (do
    (require '[com.stuartsierra.component.repl :refer [system]])

    (def streams (:topology (:topology system)))
    (def store (:out-topic (:topology system))))


  (def meta-vec (-> (:topology (:topology system))
                  (.allMetadataForStore (:out-topic (:topology system)))))
  (first meta-vec)
  (-> meta-vec first .host)
  (-> meta-vec first .port)

  ; get all the aois
  (let [meta-vec (-> (:topology (:topology system))
                   (.allMetadataForStore (:out-topic (:topology system))))]
    (map #(-> (client/get
                (str "http://" (-> % .host) ":" (-> % .port) "/aois")
                {:throw-exceptions false
                 :accept           :edn})
            :body)
      meta-vec))

  (let [s (-> streams
            (.store store (QueryableStoreTypes/keyValueStore)))
        k (.all s)]
    (into {}
      (map (fn [x]
             {(.key x) (.value x)})
        (iterator-seq k))))

  ())