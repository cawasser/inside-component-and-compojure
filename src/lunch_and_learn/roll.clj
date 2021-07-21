(ns lunch-and-learn.roll
  (:require [compojure.api.sweet :as sw]
            [ring.util.http-response :as resp]
            [schema.core :as s]))

(s/defschema Result
  {:result s/Int})


(s/defschema Request
  {:num s/Int
   :sides s/Int})


(s/defschema MultiResult
  {:total s/Int
   :roll [s/Int]})


(defn roll-dice [num sides]
  (let [roll (take num (repeatedly #(inc (rand-int sides))))]
    {:total (reduce + roll)
     :roll roll}))


(def routes
  [(sw/GET "/roll" []
     :return Result
     :summary "Rolls a six sided die"
     (resp/ok {:result (inc (rand-int 6))}))

   (sw/GET "/roll/:n/:s" []
     :path-params [n :- s/Int
                   s :- s/Int]
     :return MultiResult
     :summary "Rolls :n dice of sides :s  from a (def) form"
     (resp/ok (roll-dice n s)))

   (sw/POST "/roll" []
     :body [{:keys [num sides]} Request]
     :return MultiResult
     :summary "Given a correct request body with keys :num and :sides, returns result of roll(s)  from a (def) form"
     (resp/ok (roll-dice num sides)))])