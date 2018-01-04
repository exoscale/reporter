(defproject spootnik/reporter "0.1.21"
  :description "error and event reporting component"
  :url "https://github.com/pyr/reporter"
  :license {:name "MIT/ISC"}
  :profiles {:dev {:global-vars    {*warn-on-reflection* true}
                   :resource-paths ["test/resources"]
                   :dependencies   [[org.slf4j/slf4j-api        "1.7.25"]
                                    [org.slf4j/slf4j-log4j12    "1.7.25"]
                                    [org.clojure/tools.logging  "0.4.0"]]}}
  :dependencies [[org.clojure/clojure        "1.9.0"]
                 [org.clojure/tools.logging  "0.4.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [spootnik/raven             "0.1.3"]
                 [spootnik/net               "0.3.3-beta13"]
                 [spootnik/uncaught          "0.5.3"]
                 [metrics-clojure            "2.10.0"]
                 [metrics-clojure-riemann    "2.10.0"]
                 [metrics-clojure-jvm        "2.10.0"]
                 [metrics-clojure-graphite   "2.10.0"]])
