(ns lunch-and-learn.api
  (:require [compojure.api.sweet :as sw]
            [ring.util.http-response :as resp]
            [schema.core :as s]
            [lunch-and-learn.roll :as roll]))



(def app
  (sw/api
    {:swagger
     {:ui "/api"
      :spec "/swagger.json"
      :data {:info {:title "Dice-api"
                    :description "Compojure Api example"}
             :tags [{:name "api", :description "some apis"}]}}}

    (sw/context "/" []
      :tags ["api"]

      roll/routes

      ;pizza/routes

      (sw/GET "/" []
        :return s/Str
        :summary "the main page"
        (resp/ok "<h1>This is the Intro Page</h1>")))))






(comment
  ;reitit version
  ["/plus"
   {:post {:summary "plus with spec body parameters"
           :parameters {:body {:x int?, :y int?}}
           :responses {200 {:body {:total int?}}}
           :handler (fn [{{{:keys [x y]} :body} :parameters}]
                      (prn "plus" x y)
                      {:status 200
                       :body {:total (+ x y)}})}}]

  ; compojure version
  (GET "/plus" []
    :return {:result Long}
    :query-params [x :- Long, y :- Long]
    :summary "adds two numbers together"
    (ok {:result (+ x y)}))

  ())

