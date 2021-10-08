(ns spootnik.reporter.specs-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [spootnik.reporter.specs]))

(def valid-reporter-config
  {:sentry {:dsn "https://dummy:dsn@errors.sentry-host.com/31337"}
   :prometheus {:port 8007}
   :pushgateway {:host "localhost"
                 :job :foo
                 :port 9091
                 :metrics [{:name :foo-bar 
                            :type :gauge 
                            :help "Lorem Ipsum"
                            :label-names [:foo :bar]}]}
   :riemann {:host     "riemann.svc"
             :port     5554
             :protocol "tls"

             :defaults {:ttl  600
                        :host "localhost"
                        :tags ["cpu" "graph"]}

             :tls {:cert      "/etc/riemann/ssl/cert.pem"
                   :authority "/etc/riemann/ssl/ca.pem"
                   :pkey      "/etc/riemann/ssl/key.pkcs8"}}
   :metrics {:reporters {:riemann {:interval 10
                                   :opts     {:ttl       20
                                              :tags      ["cpu" "graph"]
                                              :host-name "localhost"}}}}})

(deftest reporter-spec-validates-correctly-test
  (testing "A correct spec should be valid"
    (is (empty? (s/explain-data :spootnik.reporter/config valid-reporter-config))))

  ;; Test a couple of locations, dont need to be exhaustive
  (testing "Prometheus port should be a positive int"
    (is (some? (s/explain-data :spootnik.reporter/config (assoc-in valid-reporter-config [:prometheus :port] -1)))))

  (testing "Prometheus host should not be empty"
    (is (some? (s/explain-data :spootnik.reporter/config (assoc-in valid-reporter-config [:prometheus :host] nil)))))

  (testing "Riemann host should not be empty"
    (is (some? (s/explain-data :spootnik.reporter/config (assoc-in valid-reporter-config [:riemann :host] nil)))))

  (testing "PushGateway host should not be empty"
    (is (some? (s/explain-data :spootnik.reporter/config (assoc-in valid-reporter-config [:pushgateway :host] nil)))))

  (testing "Metrics Reporters set is fixed"
    (is (some? (s/explain-data :spootnik.reporter/config (assoc-in valid-reporter-config [:metrics :reporters] {:foundationdb {}}))))))
