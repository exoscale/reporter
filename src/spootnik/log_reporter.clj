(ns spootnik.log-reporter
  (:require [metrics.core  :refer [default-registry]]
            [metrics.reporters :as mrep]
            [clojure.string :as str])
  (:import java.util.concurrent.TimeUnit
           [com.codahale.metrics MetricRegistry ScheduledReporter
            Slf4jReporter Slf4jReporter$LoggingLevel MetricFilter]))

(defn ^Slf4jReporter$LoggingLevel ->level
  [x]
  (cond
    (nil? x)     Slf4jReporter$LoggingLevel/INFO
    (keyword? x) (-> x name str/upper-case Slf4jReporter$LoggingLevel/valueOf)
    (string? x)  (-> x str/upper-case Slf4jReporter$LoggingLevel/valueOf)
    :else        (throw (IllegalArgumentException. "bad logging level"))))

(defn ^com.codahale.metrics.Slf4jReporter reporter
  ([opts]
   (reporter default-registry opts))
  ([^MetricRegistry reg opts]
   (let [b (Slf4jReporter/forRegistry reg)]
     (.withLoggingLevel b (->level (:level opts)))
     (when-let [^MetricFilter f (:filter opts)]
       (.filter b f))
     (when-let [^TimeUnit ru (:rate-unit opts)]
       (.convertRatesTo b ru))
     (when-let [^TimeUnit du (:duration-unit opts)]
       (.convertDurationsTo b du))
     (.build b))))

(defn start
  "Report all metrics to standard out periodically"
  [^ScheduledReporter r ^long seconds]
  (mrep/start r seconds))

(defn stop
  "Stops reporting all metrics to standard out."
  [^ScheduledReporter r]
  (mrep/stop r))
