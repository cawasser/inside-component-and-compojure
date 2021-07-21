(ns lunch-and-learn.some-endpoints-test
  (:require [clojure.test :refer :all]
            [lunch-and-learn.some-endpoints :as sut]))


(deftest format-ideal-test
  (testing "format an 'ideal'"
    (is (= (sut/format-ideal "testing" "testing")
          [:div.container.px-2
           [:div.row.gx-3
            {:style "text-align:center"}
            [:div.col [:p.bg-info.fs-2 "testing"]]
            [:div.col [:p.bg-success.text-white.fs-2 "testing"]]]]))))


