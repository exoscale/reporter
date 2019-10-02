(defproject exoscale/reporter "0.1.39"
  :description "error and event reporting component"
  :url "https://github.com/exoscale/reporter"
  :license {:name "MIT/ISC"}
  :profiles {:dev {:global-vars    {*warn-on-reflection* true}
                   :resource-paths ["test/resources"]
                   :dependencies   [[org.slf4j/slf4j-api        "1.7.26"]
                                    [org.slf4j/slf4j-log4j12    "1.7.26"]
                                    [org.clojure/tools.logging  "0.4.1"]]}}
  :dependencies [[org.clojure/clojure                   "1.10.0"]
                 [org.clojure/tools.logging             "0.4.1"]
                 [com.stuartsierra/component            "0.4.0"]
                 [exoscale/raven                        "0.4.6"]
                 [spootnik/uncaught                     "0.5.5"]
                 [metrics-clojure                       "2.10.0"]
                 [metrics-clojure-riemann               "2.10.0"]
                 [metrics-clojure-jvm                   "2.10.0"]
                 [metrics-clojure-graphite              "2.10.0"]
                 [io.prometheus/simpleclient            "0.6.0"]
                 [io.prometheus/simpleclient_common     "0.6.0"]
                 [io.prometheus/simpleclient_hotspot    "0.6.0"]
                 [io.prometheus/simpleclient_dropwizard "0.6.0"]])
