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
            [com.stuartsierra.component :as component]))


(def app-config {"bootstrap.servers"                     "localhost:9092"
                 StreamsConfig/APPLICATION_ID_CONFIG     "aoi-state"
                 StreamsConfig/COMMIT_INTERVAL_MS_CONFIG 500
                 ConsumerConfig/AUTO_OFFSET_RESET_CONFIG "latest"
                 "acks"                                  "all"
                 "retries"                               "0"
                 "cache.max.bytes.buffering"             "0"})


(defn topic-config [name]
  {:topic-name         name
   :partition-count    1
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


(defn start! [in-topic out-topic]
  (let [streams-builder (j/streams-builder)
        topo            (pipeline streams-builder in-topic out-topic)
        _               (println (-> topo j/streams-builder* .build .describe .toString))
        kafka-streams   (j/kafka-streams topo app-config)]
    (j/start kafka-streams)
    kafka-streams))


(defrecord KafkaTopology [in-topic out-topic topology]
  component/Lifecycle
  (start [component]

    (let [topology (start! in-topic out-topic)]
      (assoc component :topology topology
        :in-topic in-topic
        :out-topic out-topic)))

  (stop [component]
    (j/close topology)
    (assoc component :topology topology)))


(defn new-kafka-topology
  [in-topic out-topic]
  (map->KafkaTopology {:in-topic in-topic
                       :out-topic out-topic}))


; debug starting the Kafka Stream topology
(comment
  (def streams-builder (j/streams-builder))
  (def topo            (pipeline streams-builder "aois" "aoi-state"))
  (println (-> topo j/streams-builder* .build .describe .toString))
  (def kafka-streams   (j/kafka-streams topo app-config))

  (j/start kafka-streams)


  ())


; send some events to "aois" topic using Kafka
(comment
  (defn produce-one
    ([topic k v]
     (with-open [producer (jc/producer app-config (topic-config topic))]
       @(jc/produce! producer (topic-config topic) k v))))


  (produce-one "aois"
    {:aoi "alpha"}
    {:event-type :aoi-added
     :aoi-needs  #{[7 7 "hidef" 0]}
     :aoi        "alpha"})

  ())