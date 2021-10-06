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
   :riemann {:host     "infra-mon-pp001.gv2.p.exoscale.net"
             :port     5554
             :protocol "tls"

             :defaults {:ttl  600
                        :host "bundes-volumes-preprod.gva2"
                        :tags ["bundes-volumes" "graph"]}

             :tls {:cert      "/etc/host-certificate/ssl/cert.pem"
                   :authority "/etc/host-certificate/ssl/ca.pem"
                   :pkey      "/etc/host-certificate/ssl/key.pkcs8"}}
   :metrics {:reporters {:riemann {:interval 10
                                   :opts     {:ttl       20
                                              :tags      ["bundes-volumes" "graph"]
                                              :host-name "bundes-volumes-preprod.gva2"}}}}})

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
