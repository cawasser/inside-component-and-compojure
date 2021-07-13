(ns lunch-and-learn.router
  (:require [compojure.core :refer :all]
            [lunch-and-learn.my-resource :as resources]
            [lunch-and-learn.some-endpoints :as endpoints]))


(defroutes app
  #'endpoints/handler
  #'resources/handler
  #'resources/not-found)


