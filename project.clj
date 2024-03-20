(defproject exoscale/reporter "1.0.1"
  :description "error and event reporting component"
  :url "https://github.com/exoscale/reporter"
  :license {:name "MIT/ISC"}
  :profiles {:dev {:global-vars    {*warn-on-reflection* true}
                   :resource-paths ["test/resources"]
                   :dependencies   [[com.soundcloud/prometheus-clj "2.4.1"]
                                    [org.slf4j/slf4j-api           "1.7.26"]
                                    [org.slf4j/slf4j-log4j12       "1.7.26"]
                                    [org.clojure/tools.logging     "0.4.1"]]}}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :dependencies [[aleph                                  "0.4.7-alpha5"]
                 [org.clojure/clojure                    "1.10.1"]
                 [org.clojure/tools.logging              "1.0.0"]
                 [com.stuartsierra/component             "1.0.0"]
                 [io.sentry/sentry-clj                   "7.4.213"]
                 [spootnik/uncaught                      "0.5.5"]
                 [metrics-clojure                        "2.10.0"]
                 [metrics-clojure-riemann                "2.10.0"]
                 [metrics-clojure-jvm                    "2.10.0"]
                 [metrics-clojure-graphite               "2.10.0"]
                 [io.prometheus/simpleclient             "0.12.0"]
                 [io.prometheus/simpleclient_common      "0.12.0"]
                 [io.prometheus/simpleclient_hotspot     "0.12.0"]
                 [io.prometheus/simpleclient_dropwizard  "0.12.0"]
                 [io.prometheus/simpleclient_pushgateway "0.12.0"]
                 [camel-snake-kebab                      "0.4.2"]
                 [javax.xml.bind/jaxb-api                "2.4.0-b180830.0359"]])
