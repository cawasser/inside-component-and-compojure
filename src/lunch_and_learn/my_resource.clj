(ns lunch-and-learn.my-resource
  (:require [compojure.core :refer :all]
            [compojure.route :as route]))


; routes are just macros wrapped in more macros

(def handler
  (routes
    (GET "/" req "<h1>hello world, from the REPL!</h1>")))


(def not-found
  (route/not-found "<h1>Page not found</h1>"))
