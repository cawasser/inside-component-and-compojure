(ns lunch-and-learn.some-endpoints
  (:require [compojure.core :refer :all]
            [hiccup.core :refer :all]
            [hiccup.page :refer [include-css html5]]))


(defn- another-endpoint [req]
  (tap> req)
  (html5 {:lang "en"}
    [:head]
    [:body
     [:h3 "This is ANOTHER endpoint!"]
     [:p "remote addr:"]
     [:p (:remote-addr req)]
     [:p "did we get anything?"]]))

(defn format-ideal [title text]
  [:div.container.px-2
   [:div.row.gx-3 {:style "text-align:center"}
    [:div.col
     [:p.bg-info.fs-2 title]]
    [:div.col
     [:p.bg-success.text-white.fs-2 text]]]])

(defn- the-five-ideals [req]
  (tap> req)
  (html5 {:lang "en"}
    [:head
     [:title "The Five Ideals by Gene Kim"]
     (include-css "https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css")]
    [:body
     [:h3 "The Five Ideals of Software"]
     (format-ideal "THE FIRST IDEAL:" "Locality and Simplicity")
     (format-ideal "THE SECOND IDEAL:" "Focus, Flow, and Joy")
     (format-ideal "THE THIRD IDEAL:" "Improvement of Daily Work")
     (format-ideal "THE FOURTH IDEAL:" "Psychological Safety")
     (format-ideal "THE FIFTH IDEAL:" "Customer Focus")]))

(def handler
  (routes
    (GET "/index" req "<h1>This is the index link! which is directly in the routes</h1>")
    (GET "/another" req (#'another-endpoint req))
    (GET "/ideals" req (#'the-five-ideals req))))



(comment
  (format-ideal "testing" "testing")

  ())