(ns spootnik.reporter.macros-test
  (:require [clojure.test :refer :all]
            [spootnik.reporter :as reporter]
            [manifold.deferred :as d])
  (:import [clojure.lang ExceptionInfo]))

(defn- make-reporter []
  (reporter/initialize! {:metrics {:reporters {:console {:interval 100}}}}))

(defn- metrics [rptr]
  ;; see https://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/MetricRegistry.html
  (into #{} (-> rptr :registry .getMetrics keys)))

(defn- some-random-fn [& args]
  (apply + args))

(deftest with-time-macro-works-over-normal-values
  (testing "with-time! macro works over a normal value"
    (let [rptr   (make-reporter)
          result (reporter/with-time! [:value] 1)]
      (is (= result 1))
      (is (contains? (metrics rptr) "value"))))

  (testing "with-time! macro works over a function invocation"
    (let [rptr   (make-reporter)
          result (reporter/with-time! [:value] {:result (some-random-fn 1 2 3)})]
      (is (= result {:result 6}))
      (is (contains? (metrics rptr) "value")))))

(deftest with-time-macro-works-over-deferreds
  (testing "with-time! macro works over a deferred"
    (let [rptr   (make-reporter)
          result (reporter/with-time! [:success] (d/success-deferred 1))]
      (is (= @result 1))
      (is (contains? (metrics rptr) "success"))))

  (testing "with-time! macro works over a failed deferred"
    (let [rptr   (make-reporter)
          result (reporter/with-time! [:error] (d/error-deferred {:error true}))]
      (is (thrown? ExceptionInfo @result))
      (is (contains? (metrics rptr) "error")))))
