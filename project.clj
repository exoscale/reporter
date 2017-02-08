(defproject spootnik/reporter "0.1.14"
  :description "error and event reporting component"
  :url "https://github.com/pyr/reporter"
  :license {:name "MIT/ISC"}
  :dependencies [[org.clojure/clojure        "1.9.0-alpha14"]
                 [org.clojure/tools.logging  "0.3.1"]
                 [com.stuartsierra/component "0.3.2"]
                 [spootnik/raven             "0.1.2"]
                 [spootnik/net               "0.3.3-beta9"]
                 [spootnik/uncaught          "0.5.3"]
                 [prismatic/schema           "1.0.4"]
                 [metrics-clojure            "2.6.1"]
                 [metrics-clojure-riemann    "2.6.1"]
                 [metrics-clojure-jvm        "2.6.1"]
                 [metrics-clojure-graphite   "2.6.1"]])
