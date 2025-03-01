(ns spootnik.reporter.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string     :as str]))

;; SSL

(s/def :spootnik.reporter.config/bundle string?)
(s/def :spootnik.reporter.config/password string?)
(s/def :spootnik.reporter.config/ssl-bundle
  (s/keys :req-un [:spootnik.reporter.config/bundle
                   :spootnik.reporter.config/password]))
(s/def :spootnik.reporter.config/cert string?)
(s/def :spootnik.reporter.config/ca-cert string?)
(s/def :spootnik.reporter.config/authorities (s/coll-of string?))
(s/def :spootnik.reporter.config/pkey string?)
(s/def :spootnik.reporter.config/ssl-cert (s/keys :req-un [:spootnik.reporter.config/cert (or :spootnik.reporter.config/ca-cert :spootnik.reporter.config/authorities) :spootnik.reporter.config/pkey]))
(s/def :spootnik.reporter.config/ssl (s/or :bundle :spootnik.reporter.config/ssl-bundle :cert :spootnik.reporter.config/ssl-cert))

;; Generic
(s/def :spootnik.reporter.config/port pos-int?)
(s/def :spootnik.reporter.config/host string?)
(s/def :spootnik.reporter.config/protocol string?)
(s/def :spootnik.reporter.config/tls (s/nilable :spootnik.reporter.config/ssl-cert))
(s/def :spootnik.reporter.config/endpoint (s/and string? #(str/starts-with? % "/")))

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

;; pushgateway
(s/def :spootnik.reporter.config.pushgateway/job keyword?)
(s/def :spootnik.reporter.config.pushgateway/name keyword?)
(s/def :spootnik.reporter.config.pushgateway/type #{:gauge :counter})
(s/def :spootnik.reporter.config.pushgateway/help string?)
(s/def :spootnik.reporter.config.pushgateway/label-name keyword?)
(s/def :spootnik.reporter.config.pushgateway/label-names (s/coll-of :spootnik.reporter.config.pushgateway/label-name))
(s/def :spootnik.reporter.config.pushgateway/grouping-keys (s/map-of :spootnik.reporter.config.pushgateway/label-name string?))
(s/def :spootnik.reporter.config.pushgateway/metric (s/keys :req-un [:spootnik.reporter.config.pushgateway/name
                                                                     :spootnik.reporter.config.pushgateway/type
                                                                     :spootnik.reporter.config.pushgateway/help
                                                                     :spootnik.reporter.config.pushgateway/label-names]))
(s/def :spootnik.reporter.config.metrics/pushgateway (s/coll-of :spootnik.reporter.config.pushgateway/metric))
(s/def :spootnik.reporter.config.pushgateway/pushgateway (s/keys :req-un [:spootnik.reporter.config/host
                                                                          :spootnik.reporter.config.pushgateway/job]
                                                                 :opt-un [:spootnik.reporter.config/tls
                                                                          :spootnik.reporter.config.pushgateway/grouping-keys
                                                                          :spootnik.reporter.config/port]))

;; generic metrics reporter
(s/def :spootnik.reporter.config.metrics.reporter.config/opts map?)
(s/def :spootnik.reporter.config.metrics.reporter.config/interval pos-int?)
(s/def :spootnik.reporter.config.metrics.reporter/gen-config
  (s/keys :req-un []
          :opt-un [:spootnik.reporter.config.metrics.reporter.config/interval
                   :spootnik.reporter.config.metrics.reporter.config/opts]))

(s/def :spootnik.reporter.config.metrics/graphite :spootnik.reporter.config.metrics.reporter/gen-config)
(s/def :spootnik.reporter.config.metrics/prometheus :spootnik.reporter.config.metrics.reporter/gen-config)
(s/def :spootnik.reporter.config.metrics/riemann :spootnik.reporter.config.metrics.reporter/gen-config)
(s/def :spootnik.reporter.config.metrics/console :spootnik.reporter.config.metrics.reporter/gen-config)
(s/def :spootnik.reporter.config.metrics/jmx :spootnik.reporter.config.metrics.reporter/gen-config)
;; metrics

(s/def :spootnik.reporter.config.metrics/reporters
  (s/keys :opt-un [:spootnik.reporter.config.metrics/graphite
                   :spootnik.reporter.config.metrics/prometheus
                   :spootnik.reporter.config.metrics/riemann
                   :spootnik.reporter.config.metrics/console
                   :spootnik.reporter.config.metrics/jmx
                   :spootnik.reporter.config.metrics/pushgateway]))
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

;; General config
(s/def :spootnik.reporter.config/prevent-capture? boolean?)
(s/def :spootnik.reporter/config
  (s/keys :req-un []
          :opt-un [:spootnik.reporter.config/prevent-capture?
                   :spootnik.reporter.config/sentry
                   :spootnik.reporter.config/metrics
                   :spootnik.reporter.config/riemann
                   :spootnik.reporter.config/prometheus
                   :spootnik.reporter.config.pushgateway/pushgateway]))
