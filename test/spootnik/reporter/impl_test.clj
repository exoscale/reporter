(ns spootnik.reporter.impl-test
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [prometheus.core :as prometheus]
            [spootnik.reporter.impl :refer :all]
            [com.stuartsierra.component :as component]
            [raven.client :refer [http-requests-payload-stub]]
            [clojure.set :as cljset])
  (:import io.prometheus.client.CollectorRegistry
           io.netty.handler.ssl.SslContextBuilder
           io.netty.handler.ssl.ClientAuth
           java.lang.String))

(deftest timers-do-not-modify-the-world
  (let [reporter (component/start (map->Reporter {:metrics {:reporters {:console {:interval 100}}}}))]

    (is (= (time! reporter :one (+ 1 2))
           (time! nil :one (+ 1 2))))

    (is (= (time! reporter :two (do (Thread/sleep 200) "foo"))
           (time! nil :two "foo")))

    (component/stop reporter)))

(deftest prometheus-exporter-test
  (let [reporter (component/start (map->Reporter {:metrics {:reporters {:prometheus {}}}}))]

    (time! reporter [:timer :promtest] (+ 1 2))
    (let [metrics (prometheus-str-metrics)]
      (is (.contains ^String metrics "TYPE timer_promtest summary")))
    (component/stop reporter)))

(def sample-port 9058)

(deftest prometheus-server-test
  (let [port     sample-port
        system   (map->Reporter {:prometheus {:port port}
                                 :metrics    {:reporters {:prometheus {}}}})
        reporter (component/start system)
        jvm-help "# HELP jvm_threads_current Current thread count of a JVM"]
    (Thread/sleep 5000)
    (testing (str "metrics served on port " port)
      (let [res @(http/get (format "http://localhost:%s/metrics" port))]
        (is (some-> res :body bs/to-string (str/includes? jvm-help)))))
    (component/stop reporter)))

(deftest prometheus-native-metrics-test
  (let [port     sample-port
        system   (map->Reporter {:prometheus {:port port}
                                 :metrics    {:reporters {:prometheus {}}}})
        reporter (component/start system)
        store    (:prometheus reporter)
        _        (prometheus/init-defaults)
        store    (prometheus/register-counter
                  store
                  "test"
                  "native_counter"
                  "do you even increment?"
                  ["test"])]
    (prometheus/increase-counter store "test" "native_counter" ["test"] 42)
    (let [metrics (prometheus-str-metrics)]
      (testing "store a native prometheus metric in the registry"
        (is (str/includes? metrics "do you even increment?")))
      (testing "increment a native counter and retreive the result"
        (is (str/includes? metrics "test_native_counter_total{test=\"test\",} 42.0"))))
    (.clear ^CollectorRegistry (:registry store))
    (component/stop reporter)))

(defn ->resource
  [path]
  (io/file (io/resource path)))

(def client-context
  (-> (SslContextBuilder/forClient)
      (.trustManager (->resource "ca-cert.pem"))
      (.keyManager (->resource "client-cert.pem") (->resource "client-key.pem"))
      .build))

(deftest prometheus-tls-auth
  (let [port       sample-port
        prometheus {:prometheus {:port port
                                 :tls  {:pkey    (->resource "server-key.pem")
                                        :cert    (->resource "server-cert.pem")
                                        :ca-cert (->resource "ca-cert.pem")}}
                    :metrics    {:reporters {:prometheus {}}}}
        system     (map->Reporter prometheus)
        reporter   (component/start system)]
    (testing "with tls configured, a request with valid certs should succeed"
      (is (= 200
             (:status @(http/get (format "https://localhost:%s/metrics" port)
                                 {:pool (http/connection-pool
                                         {:connection-options
                                          {:ssl-context client-context}})})))))
    (testing "with tls configured, a request without valid certs should fail"
      (is (thrown?
           clojure.lang.ExceptionInfo
           @(http/get (format "https://localhost:%s/metrics" port)))))
    (component/stop reporter)))

(deftest prometheus-metrics-endpoint
  (let [port       sample-port
        prometheus {:prometheus {:port port
                                 :endpoint "/metrics"}
                    :metrics    {:reporters {:prometheus {}}}}
        system     (map->Reporter prometheus)
        reporter   (component/start system)]
    (testing "metrics are served at specified endpoint in config"
      (is (= 200
             (:status @(http/get (format "http://localhost:%s/metrics" port))))))
    (testing "requests to non-specified endpoint return 404"
      (is (thrown?
           clojure.lang.ExceptionInfo
           @(http/get (format "http://localhost:%s/nothing" port)))))
    (component/stop reporter)))

(deftest sentry-sends-events
  (testing "we can send events to sentry using the :memory: backend"
    (let [reporter (component/start (map->Reporter {:sentry {:dsn ":memory:"}
                                                    :metrics {:reporters {:console {:interval 100}}}}))]

      (.capture! ^spootnik.reporter.impl.SentrySink reporter {:message "A simple test event"})
      (is (= "A simple test event" (:message (first @http-requests-payload-stub))))

      (component/stop reporter))))

(deftest pushgateway-send-events
  (testing "Sending events to pushgateway"
    (let [reporter (component/start (map->Reporter {:metrics {:reporters {:pushgateway  [{:name
                                                                                          :foo_counter
                                                                                          :help "A Counter"
                                                                                          :type :counter
                                                                                          :label-names [:bar :baz]}
                                                                                         {:name
                                                                                          :foo-gauge
                                                                                          :help "A Gauge"
                                                                                          :type :gauge
                                                                                          :label-names [:bar :baz]}
                                                                                         {:name
                                                                                          :foo-gauge-b
                                                                                          :help "YAG"
                                                                                          :type :gauge
                                                                                          :label-names [:bar :baz]}]}}
                                                    :pushgateway {:host "localhost"
                                                                  :job "testing"
                                                                  :grouping-keys {:cluster "testing-cluster"}
                                                                  :port 9091}}))
          metrics-to-push [[:counter :foo_counter  ["taba" "tec"] 0]
                           [:counter :foo_counter  ["taba" "zar"] 0]
                           [:gauge   :foo-gauge    ["taba" "tec"] 2]
                           [:gauge   :foo-gauge    ["taba" "zar"] 3]
                           [:gauge   :foo-gauge-b  ["taba" "tec"] 4]
                           [:gauge   :foo-gauge    ["taba" "tec"] 5]
                           [:counter :foo_counter  ["taba" "tec"] 0]]]

      (doseq [metric metrics-to-push]
        (let [[type name label-values value] metric]
          (condp = type
            :counter
            (.counter! ^spootnik.reporter.impl.PushGatewaySink reporter {:name name
                                                                         :label-values label-values})
            :gauge
            (.gauge! ^spootnik.reporter.impl.PushGatewaySink reporter {:name name
                                                                       :value value
                                                                       :label-values label-values}))))

      (let [pg-metrics (-> @(http/get "http://localhost:9091/metrics")
                           :body
                           bs/to-string
                           clojure.string/split-lines
                           set)
            expected   #{"foo_counter_total{bar=\"taba\",baz=\"tec\",cluster=\"testing-cluster\",instance=\"\",job=\"testing\"} 2"
                         "foo_counter_total{bar=\"taba\",baz=\"zar\",cluster=\"testing-cluster\",instance=\"\",job=\"testing\"} 1"
                         "foo_gauge{bar=\"taba\",baz=\"tec\",cluster=\"testing-cluster\",instance=\"\",job=\"testing\"} 5"
                         "foo_gauge{bar=\"taba\",baz=\"zar\",cluster=\"testing-cluster\",instance=\"\",job=\"testing\"} 3"
                         "foo_gauge_b{bar=\"taba\",baz=\"tec\",cluster=\"testing-cluster\",instance=\"\",job=\"testing\"} 4"}]
        (is (true? (cljset/subset? expected pg-metrics))))

      (component/stop reporter))))
