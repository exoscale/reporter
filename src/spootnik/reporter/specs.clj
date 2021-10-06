(ns spootnik.reporter.specs
  (:require [clojure.spec.alpha :as s]))


;; SSL

(s/def :spootnik.reporter.config/bundle string?)
(s/def :spootnik.reporter.config/password string?)
(s/def :spootnik.reporter.config/ssl-bundle
  (s/keys :req-un [:spootnik.reporter.config/bundle
                   :spootnik.reporter.config/password]))
(s/def :spootnik.reporter.config/cert string?)
(s/def :spootnik.reporter.config/ca-cert string?)
(s/def :spootnik.reporter.config/authority string?)
(s/def :spootnik.reporter.config/pkey string?)
(s/def :spootnik.reporter.config/ssl-cert (s/keys :req-un [:spootnik.reporter.config/cert :spootnik.reporter.config/authority :spootnik.reporter.config/pkey]))
(s/def :spootnik.reporter.config/ssl (s/or :bundle :spootnik.reporter.config/ssl-bundle :cert :spootnik.reporter.config/ssl-cert))

;; Generic
(s/def :spootnik.reporter.config/port pos-int?)
(s/def :spootnik.reporter.config/host string?)
(s/def :spootnik.reporter.config/protocol string?)
(s/def :spootnik.reporter.config/tls :spootnik.reporter.config/ssl-cert)
(s/def :spootnik.reporter.config/endpoint (s/and string? #(clojure.string/starts-with? % "/")))

;; riemann
(s/def :spootnik.reporter.config.riemann/batch pos-int?)
(s/def :spootnik.reporter.config.riemann/defaults any?)

(s/def :spootnik.reporter.config/riemann
  (s/keys :req-un [:spootnik.reporter.config/host]
          :opt-un [:spootnik.reporter.config/port
                   :spootnik.reporter.config/protocol
                   :spootnik.reporter.config.riemann/batch
                   :spootnik.reporter.config.riemann/defaults
                   :spootnik.reporter.config/tls]))

;; single metrics reporter
(s/def :spootnik.reporter.config.metrics.reporter.config/opts map?)
(s/def :spootnik.reporter.config.metrics.reporter.config/interval pos-int?)
(s/def :spootnik.reporter.config.metrics.reporter/config
  (s/keys :req-un []
          :opt-un [:spootnik.reporter.config.metrics.reporter.config/interval
                   :spootnik.reporter.config.metrics.reporter.config/opts]))

;; metrics
(s/def :spootnik.reporter.config.metrics/reporters
  (s/map-of #{:graphite :prometheus :riemann :console :jmx}
            :spootnik.reporter.config.metrics.reporter/config))
(s/def :spootnik.reporter.config/metrics
  (s/keys :req-un [:spootnik.reporter.config.metrics/reporters]))

;; sentry
(s/def :spootnik.reporter.config.sentry/dsn string?)
(s/def :spootnik.reporter.config/sentry
  (s/keys :req-un [:spootnik.reporter.config.sentry/dsn]))


;; prometheus
(s/def :spootnik.reporter.config/prometheus (s/keys :req-un [:spootnik.reporter.config/port]
                                                    :opt-un [:spootnik.reporter.config/tls
                                                             :spootnik.reporter.config/endpoint
                                                             :spootnik.reporter.config/host]))

;; pushgateway

(s/def :spootnik.reporter.pushgateway-config/job keyword?)
(s/def :spootnik.reporter.pushgateway-config/name keyword?)
(s/def :spootnik.reporter.pushgateway-config/type #{:gauge :counter})
(s/def :spootnik.reporter.pushgateway-config/help string?)
(s/def :spootnik.reporter.pushgateway-config/label-names (s/coll-of keyword?))
(s/def :spootnik.reporter.pushgateway-config/metric (s/keys :req-un [:spootnik.reporter.pushgateway-config/name
                                                                     :spootnik.reporter.pushgateway-config/type
                                                                     :spootnik.reporter.pushgateway-config/help
                                                                     :spootnik.reporter.pushgateway-config/label-names]))
(s/def :spootnik.reporter.pushgateway-config/metrics (s/coll-of :spootnik.reporter.pushgateway-config/metric))
(s/def :spootnik.reporter.pushgateway-config/pushgateway (s/keys :req-un [:spootnik.reporter.config/host
                                                                          :spootnik.reporter.pushgateway-config/job
                                                                          :spootnik.reporter.pushgateway-config/metrics]
                                                                 :opt-un [:spootnik.reporter.config/tls
                                                                          :spootnik.reporter.config/port]))

;; General config
(s/def :spootnik.reporter.config/prevent-capture? boolean?)
(s/def :spootnik.reporter/config
  (s/keys :req-un []
          :opt-un [:spootnik.reporter.config/prevent-capture?
                   :spootnik.reporter.config/sentry
                   :spootnik.reporter.config/metrics
                   :spootnik.reporter.config/riemann
                   :spootnik.reporter.config/prometheus
                   :spootnik.reporter.pushgateway-config/pushgateway]))

