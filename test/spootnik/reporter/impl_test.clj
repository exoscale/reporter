(ns spootnik.reporter.impl-test
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [prometheus.core :as prometheus]
            [spootnik.reporter.impl :refer :all]
            [com.stuartsierra.component :as component]
            [raven.client :refer [http-requests-payload-stub]])
  (:import io.prometheus.client.CollectorRegistry
           io.netty.handler.ssl.SslContextBuilder
           io.netty.handler.ssl.ClientAuth))

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
      (let [res  @(http/get (format "http://localhost:%s/metrics" port))
            body (:body (update res :body bs/to-string))]
        (is (str/includes? body jvm-help))))
    (component/stop reporter)))

(deftest prometheus-native-metrics-test
  (let [port     sample-port
        system   (map->Reporter {:prometheus {:port port}
                                 :metrics    {:reporters {:prometheus {}}}})
        reporter (component/start system)
        store    (:prometheus reporter)
        _        (prometheus/init-defaults)
        _        (reset! store (prometheus/register-counter
                                @store
                                "test"
                                "native_counter"
                                "do you even increment?"
                                ["test"]))]
    (prometheus/increase-counter @store "test" "native_counter" ["test"] 42)
    (let [metrics (prometheus-str-metrics)]
      (testing "store a native prometheus metric in the registry"
        (is (str/includes? metrics "do you even increment?")))
      (testing "increment a native counter and retreive the result"
        (is (str/includes? metrics "test_native_counter{test=\"test\",} 42.0"))))
    (.clear ^CollectorRegistry (:registry @store))
    (component/stop reporter)))

(defn ->resource
  [path]
  (io/file (io/resource path)))

(def client-context
  (-> (SslContextBuilder/forClient)
      (.trustManager (->resource "ca.crt"))
      (.keyManager (->resource "client.crt") (->resource "client.key"))
      .build))

(deftest prometheus-tls-auth
  (let [port       sample-port
        prometheus {:prometheus {:port port
                                 :ssl  {:pkey    (->resource "server.key")
                                        :cert    (->resource "server.crt")
                                        :ca-cert (->resource "ca.crt")}}
                    :metrics    {:reporters {:prometheus {}}}}
        system     (map->Reporter prometheus)
        reporter   (component/start system)]
    (testing "with ssl configured, a request with valid certs should succeed"
      (is (= 200
             (:status @(http/get (format "https://localhost:%s/metrics" port)
                                 {:pool (http/connection-pool
                                         {:connection-options
                                          {:ssl-context client-context}})})))))
    (testing "with ssl configured, a request without valid certs should fail"
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
