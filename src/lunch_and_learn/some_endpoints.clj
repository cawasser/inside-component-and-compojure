(ns lunch-and-learn.some-endpoints
  (:require [compojure.core :refer :all]))


(defn- another-endpoint [_]
  "<h3>This is ANOTHER endpoint! but with changes, and some more</h3>")


(def handler
  (routes
    (GET "/index" [] "<h1>This is the index link!</h1>")
    (GET "/another" [req] (#'another-endpoint req))))