(ns lunch-and-learn.api
  (:require [compojure.api.sweet :as sw]
            [ring.util.http-response :as resp]
            [schema.core :as s]
            [lunch-and-learn.roll :as roll]
            [lunch-and-learn.data-source :as ds]))



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

      (ds/queries topology))))


; the same endpoint can be expressed as data (reitit) AND macros (compojure)
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

