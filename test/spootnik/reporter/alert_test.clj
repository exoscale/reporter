(ns spootnik.reporter.alert-test
  (:require [clojure.test :as _t :refer [deftest is]]
            [matcher-combinators.test]
            [spootnik.reporter.alert :as a]))

(deftest test-alert-format
  (is (match? {:labels {:alertname "yolo"}}
              (a/alert->payload {:name "yolo"})))
  (is (match? {:labels {:alertname "yolo", :baz "bazz"
                        :key_format "format"}}
              (a/alert->payload {:name "yolo"
                                 :labels {:baz "bazz" :key-format "format"}})))
  (is (match? {:labels {:alertname "yolo", :baz "bazz"},
               :annotations {:foo "bar"}}
              (a/alert->payload {:name "yolo"
                                 :annotations {:foo "bar"}
                                 :labels {:baz "bazz"}})))
  (is (match? {:labels {:alertname "yolo"}
               :generatorURL "http://google.com"}
              (a/alert->payload {:name "yolo"
                                 :generator-url "http://google.com"}))))
