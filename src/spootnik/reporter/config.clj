(ns spootnik.reporter.config
  (:require [clojure.spec.alpha :as s]))


(s/def ::bundle string?)
(s/def ::password string?)
(s/def ::ssl-bundle (s/keys :req-un [::bundle ::password]))
(s/def ::cert string?)
(s/def ::ca-cert string?)
(s/def ::authority string?)
(s/def ::pkey string?)
(s/def ::ssl-cert (s/keys :req-un [::cert ::authority ::pkey]))
(s/def ::ssl (s/or :bundle ::ssl-bundle :cert ::ssl-cert))

(s/def ::prevent-capture? boolean?)

(s/def ::dsn string?)
(s/def ::port pos-int?)
(s/def ::host string?)
(s/def ::protocol string?)
(s/def ::batch pos-int?)
(s/def ::defaults any?)
(s/def ::tls ::ssl-cert)

(s/def ::endpoint
  (s/and string?
         #(clojure.string/starts-with? % "/")))

(s/def ::riemann (s/keys :req-un [::host] :opt-un [::port ::protocol ::batch ::defaults ::tls]))

(s/def ::opts map?)
(s/def ::interval pos-int?)
(s/def ::reporter-config (s/keys :req-un [] :opt-un [::interval ::opts]))
(s/def ::reporters (s/map-of #{:graphite :prometheus :riemann :console :jmx} ::reporter-config))
(s/def ::metrics (s/keys :req-un [::reporters]))

(s/def ::sentry (s/keys :req-un [::dsn]))
(s/def ::prometheus (s/keys :req-un [::port]
                            :opt-un [::tls
                                     ::endpoint
                                     ::host]))
(s/def ::config (s/keys :req-un []
                        :opt-un [::prevent-capture?
                                 ::sentry
                                 ::metrics
                                 ::riemann
                                 ::prometheus]))
