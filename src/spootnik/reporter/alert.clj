(ns spootnik.reporter.alert
  (:require [aleph.http :as http]
            [camel-snake-kebab.core :as csk]
            [clojure.spec.alpha :as s]
            [exoscale.ex :as ex]
            [jsonista.core :as json])
  (:import (java.time Instant)))

(defprotocol Alert
  (-send! [client alert])
  (-msend! [client alerts]))

(s/def ::named (s/or :k ident? :s string?))

(s/def :spootnik.reporter.alert.annotation/key ::named)
(s/def :spootnik.reporter.alert.annotation/value (s/or :named ::named :num number?))
(s/def :spootnik.reporter.alert/annotations
  (s/map-of :spootnik.reporter.alert.annotation/key
            :spootnik.reporter.alert.annotation/value))

(s/def :spootnik.reporter.alert.label/key
  (s/and ::named
         (fn [[_ val]] (re-matches #"[a-zA-Z_][a-zA-Z0-9_-]*"
                                   (name val)))))
(s/def :spootnik.reporter.alert.label/value (s/or :named ::named :num number?))
(s/def :spootnik.reporter.alert/labels
  (s/map-of :spootnik.reporter.alert.label/key
            :spootnik.reporter.alert.label/value))

(s/def :spootnik.reporter.alert/name :spootnik.reporter.alert.label/key)
(s/def :spootnik.reporter.alert/generator-url string?)
(s/def :spootnik.reporter.alert.options/delay-after-ms pos-int?)

(s/def :spootnik.reporter/alert
  (s/keys :req-un [:spootnik.reporter.alert/name]
          :opt-un [:spootnik.reporter.alert/labels
                   :spootnik.reporter.alert/annotations
                   :spootnik.reporter.alert/generator-url
                   :spootnik.reporter.alert.options/delay-after-ms]))

(s/def :spootnik.reporter/alerts (s/coll-of :spootnik.reporter/alert :min-count 1))

(defn alert->payload
  [{:as _alert :keys [name labels annotations generator-url delay-after-ms]
    :or {delay-after-ms (* 60 1000 5)}}]
  (let [t (Instant/now)]
    (cond-> {:startsAt (str t)
             :endsAt (str (.plusMillis t (* delay-after-ms)))
             :labels (into {:alertname (csk/->snake_case name)}
                           (map (fn [[k v]] [(csk/->snake_case k) v]))
                           labels)}

      (seq annotations)
      (assoc :annotations
             (into {}
                   (map (fn [[k v]] [(csk/->snake_case k) v]))
                   annotations))

      generator-url
      (assoc :generatorURL generator-url))))

(defn- alerts-body
  [alerts]
  (json/write-value-as-string
   (map (fn [alert] (alert->payload alert)) alerts)))

(defrecord AlephClient [server])

(s/def :spootnik.reporter.alert.client/server string?)
(s/def :spootnik.reporter.alert/client
  (s/keys :req-un [:spootnik.reporter.alert.client/server]))

(extend-protocol Alert
  AlephClient
  (-msend! [client alerts]
    @(http/post (format "%s/api/v2/alerts" (:server client))
                (into {:body (alerts-body alerts)
                       :headers {"Content-Type" "application/json; charset=utf-8"}}
                      client)))
  (-send! [client alert]
    (-msend! client [alert])))

(defn send!
  "Sends `alert`, conforming to `:spootnik.reporter/alert`, via `client` to
  alertmanager instance conforming to `:spootnik.reporter.alert/client`"
  [client alert]
  (ex/assert-spec-valid :spootnik.reporter.alert/client client)
  (ex/assert-spec-valid :spootnik.reporter/alert alert)
  (-send! client alert))

(defn msend!
  "Sends `alerts` in bulk, conforming to `:spootnik.reporter/alerts`, via `client`
  to alertmanager instance conforming to `:spootnik.reporter.alert/client`"
  [client alerts]
  (ex/assert-spec-valid :spootnik.reporter.alert/client client)
  (ex/assert-spec-valid :spootnik.reporter/alerts alerts)
  (-msend! client alerts))
