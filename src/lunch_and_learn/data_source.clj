(ns lunch-and-learn.data-source
  (:require [compojure.api.sweet :as sw]
            [ring.util.http-response :as resp]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [lunch-and-learn.data-service :as topo]))


(def data [{:id "alpha" :data-set #{[1 1 "x" 0] [1 1 "x" 1] [1 1 "x" 3]}}
           {:id "bravo" :data-set #{[9 9 "ka" 0] [9 9 "ka" 1] [9 9 "ka" 3]}}
           {:id "charlie" :data-set #{[4 4 "x" 0] [4 4 "x" 1] [4 4 "x" 3]}}])


(defn get-aois [topology]
  (->> "aoi-state"
    (topo/get-all-aois topology)
    (map (fn [[k v]]
           {(:id k) v}))
    (into {})))


(defn get-aoi [topology id]
  {:id id :data-set (topo/get-one-aoi topology "aoi-state" id)})

(topo/get-one-aoi {} "aoi-state" "bravo")

(s/defschema Cell
  [(s/one s/Num "row") (s/one s/Num "col")
   (s/one s/Str "beam") (s/one s/Num "time")])


(s/defschema Row
  {(s/optional-key :id) s/Str
   (s/optional-key :data-set) #{Cell}})


(s/defschema Coll
  [Row])


(defn queries [topology]
  [(sw/GET "/aois" []
     :return Coll
     :summary "get all the aois"
     (resp/ok (get-aois topology)))

   (sw/GET "/aoi/:id" []
     :path-params [id :- s/Str]
     :return Row
     :summary "get a single aoi given it's :id"
     (resp/ok (get-aoi topology id)))])



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
  (s/validate FancySeq ["test" 1 2 3]) ; why doesn't this work? :k is supposed to be optional


  (def tuple [(s/one s/Num "row") (s/one s/Num "col")
              (s/one s/Str "beam") (s/one s/Num "time")])

  (s/validate tuple [5 6 "dummy" 0])

  ; now we can check the Cell schema
  (s/validate Cell [0 0 "x" 0])

  ; Row schema
  (s/validate Row {:id       "alpha"
                   :data-set #{[0 0 "x" 0]}})
  (s/validate Row {:id       "alpha"
                   :data-set #{[0 0 "x" 0][0 0 "x" 1][0 0 "x" 2][0 0 "x" 3]}})
  (s/validate Row {:id       "alpha"})
  (s/validate Row {:data-set #{[0 0 "x" 0]}})


  ; Coll schema
  (s/validate Coll [{:id       "alpha"
                     :data-set #{[0 0 "x" 0][0 0 "x" 1][0 0 "x" 2][0 0 "x" 3]}}])
  (s/validate Coll [{:id       "alpha"
                     :data-set #{[0 0 "x" 0][0 0 "x" 1][0 0 "x" 2][0 0 "x" 3]}}
                    {:id       "bravo"
                     :data-set #{[9 9 "x" 0][9 9 "x" 1][9 9 "x" 2][9 9 "x" 3]}}])
  (s/validate Coll data)

  ())


; 2. try our the "queries"
(comment
  (get-aois {})

  (get-aoi {} "alpha")


  ; what happens if we don't have the id?
  (get-aoi () "delta")
  (s/validate Row (get-aoi {} "delta"))

  ())

; 3. start working up the correct query for the actual kTable data (see data2)
(comment
  (require '[com.stuartsierra.component.repl :refer [system]])

  (def topology (:topology system))

  (->> "aoi-state"
    (topo/get-all-values topology)
    (map (fn [[k v]]
           {(:aoi k) v}))
    (into {}))

  ())