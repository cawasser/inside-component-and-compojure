(ns lunch-and-learn.data-source
  (:require [compojure.api.sweet :as sw]
            [ring.util.http-response :as resp]
            [schema.core :as s]
            [lunch-and-learn.data-service :as topo]))


(def data [{:id "alpha" :data-set #{[1 1 "x" 0] [1 1 "x" 1] [1 1 "x" 3]}}
           {:id "bravo" :data-set #{[9 9 "ka" 0] [9 9 "ka" 1] [9 9 "ka" 3]}}
           {:id "charlie" :data-set #{[4 4 "x" 0] [4 4 "x" 1] [4 4 "x" 3]}}])


(defn get-aois [topology]
  (if-let [ret (topo/get-all-aois
                 (:topology topology)
                 (:host (:config topology))
                 (:port (:config topology))
                 (:out-topic topology))]
    ret
    []))


(defn get-aois-local [topology]
  (if-let [ret (topo/get-all-aois-local
                 (:topology topology)
                 (:out-topic topology))]
    ret
    []))


(defn get-aoi [topology id]
  (topo/get-one-aoi
    (:topology topology)
    (:out-topic topology)
    (:host (:config topology))
    (:port (:config topology))
    id))


(s/defschema Cell
  [(s/one s/Num "row") (s/one s/Num "col")
   (s/one s/Str "beam") (s/one s/Num "time")])


(s/defschema Row
  {(s/optional-key :id)       s/Str
   (s/optional-key :data-set) #{Cell}})


(s/defschema Coll
  [Row])


(defn queries [topology]
  [(sw/GET "/aois" []
     :return Coll
     :summary "get all the aois"
     (resp/ok (get-aois topology)))

   (sw/GET "/aois-local" []
     :return Coll
     :summary "get all the local aois"
     (resp/ok (get-aois-local topology)))

   (sw/GET "/aoi/:id" []
     :path-params [id :- s/Str]
     :return Row
     :summary "get a single aoi given it's :id"
     (resp/ok (get-aoi topology id)))])


(defn app [topology]
  (sw/api
    {:swagger
     {:ui "/api"
      :spec "/swagger.json"
      :data {:info {:title "AOI State"
                    :description "Get the current state of Areas of Interest"}
             :tags [{:name "api", :description "AOI queries"}]}}}

    (sw/context "/" []
      :tags ["api"]

      (queries topology))))



; 1. play with the schemas
(comment
  (s/validate s/Num 42)

  (def FancySeq
    "A sequence that starts with a String, followed by an optional Keyword,
     followed by any number of Numbers."
    [(s/one s/Str "s")
     (s/optional s/Keyword "k")
     s/Num])

  (s/validate FancySeq ["test"])
  (s/validate FancySeq ["test" :k])
  (s/validate FancySeq ["test" :k 1 2 3])
  (s/validate FancySeq ["test" 1 2 3])                      ; why doesn't this work? :k is supposed to be optional


  (def tuple [(s/one s/Num "row") (s/one s/Num "col")
              (s/one s/Str "beam") (s/one s/Num "time")])

  (s/validate tuple [5 6 "dummy" 0])

  ; now we can check the Cell schema
  (s/validate Cell [0 0 "x" 0])

  ; Row schema
  (s/validate Row {:id       "alpha"
                   :data-set #{[0 0 "x" 0]}})
  (s/validate Row {:id       "alpha"
                   :data-set #{[0 0 "x" 0] [0 0 "x" 1] [0 0 "x" 2] [0 0 "x" 3]}})
  (s/validate Row {:id "alpha"})
  (s/validate Row {:data-set #{[0 0 "x" 0]}})


  ; Coll schema
  (s/validate Coll [{:id       "alpha"
                     :data-set #{[0 0 "x" 0] [0 0 "x" 1] [0 0 "x" 2] [0 0 "x" 3]}}])
  (s/validate Coll [{:id       "alpha"
                     :data-set #{[0 0 "x" 0] [0 0 "x" 1] [0 0 "x" 2] [0 0 "x" 3]}}
                    {:id       "bravo"
                     :data-set #{[9 9 "x" 0] [9 9 "x" 1] [9 9 "x" 2] [9 9 "x" 3]}}])
  (s/validate Coll data)

  ())



; 2. start working up the correct query for the actual kTable data (see data2)
(comment
  (do
    (require '[com.stuartsierra.component.repl :refer [system]])

    (def topology (:topology system))
    (def host (-> system :topology :config :host))
    (def port (-> system :topology :config :port))
    (def id "alpha"))

  (keys system)

  (->> (:out-topic topology)
    (topo/get-all-aois (:topology topology) host port))


  ())


; 3. flex the query handlers
(comment
  (do
    (require '[com.stuartsierra.component.repl :refer [system]])
    (def topology (:topology system)))

  ; get all the aois
  (get-aois topology)

  (topo/get-all-aois
    (:topology topology)
    (:host (:config topology))
    (:port (:config topology))
    (:out-topic topology))



  (if-let [ret (->> (:out-topic topology)
                 (topo/get-all-aois (:topology topology)
                   (:host (:config topology)) (:port (:config topology))))]
    ret
    [])


  ; get just one aoi
  (get-aoi topology "alpha")
  (get-aoi topology "bravo")
  (get-aoi topology "delta")


  ())