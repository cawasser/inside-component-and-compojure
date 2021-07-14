(ns lunch-and-learn.some-endpoints
  (:require [compojure.core :refer :all]
            [hiccup.core :refer :all]
            [hiccup.page :refer [include-css html5]]))


(defn- another-endpoint [_]
  "<h3>This is ANOTHER endpoint! some more</h3>")

(defn- format-ideal [title text]
  [:div.container.px-2
   [:div.row.gx-3 {:style "text-align:center"}
    [:div.col
     [:p.bg-primary.text-white.fs-2 title]]
    [:div.col
     [:p.p-3.fs-4 text]]]])

(defn- the-five-ideals []
  (html5 {:lang "en"}
    [:head
     [:title "The Five Ideals by Gene Kim"]
     (include-css "https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css")]
    [:body
     (format-ideal "THE FIRST IDEAL:" "Locality and Simplicity")
     (format-ideal "THE SECOND IDEAL:" "Focus, Flow, and Joy")
     (format-ideal "THE THIRD IDEAL:" "Improvement of Daily Work")
     (format-ideal "THE FOURTH IDEAL:" "Psychological Safety")
     (format-ideal "THE FIFTH IDEAL:" "Customer Focus")]))

(def handler
  (routes
    (GET "/index" [] "<h1>This is the index link! which is directly in the routes</h1>")
    (GET "/another" [req] (#'another-endpoint req))
    (GET "/ideals" [] (#'the-five-ideals))))