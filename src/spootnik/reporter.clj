(ns spootnik.reporter
  (:require [com.stuartsierra.component :as c]
            [clojure.spec.alpha         :as s]
            [net.http.client            :as http]
            [raven.client               :as raven]
            [metrics.reporters.console  :as console]
            [metrics.reporters.jmx      :as jmx]
            [metrics.reporters.graphite :as graphite]
            [metrics.reporters.riemann  :as riemann]
            [metrics.jvm.core           :as jvm]
            [metrics.core               :as m]
            [metrics.gauges             :as gge]
            [metrics.counters           :as cnt]
            [metrics.meters             :as mtr]
            [metrics.timers             :as tmr]
            [metrics.histograms         :as hst]
            [clojure.string             :as str]
            [clojure.tools.logging      :refer [info error]]
            [spootnik.uncaught          :refer [with-uncaught]])
  (:import com.aphyr.riemann.client.RiemannClient
           com.aphyr.riemann.client.RiemannBatchClient
           com.aphyr.riemann.client.TcpTransport
           com.aphyr.riemann.client.SSL
           com.codahale.metrics.ScheduledReporter))

(defprotocol RiemannSink
  (send! [this e]))

(defprotocol SentrySink
  (capture! [this e]))

(defprotocol MetricHolder
  (instrument! [this prefix])
  (build! [this type alias] [this type alias f])
  (inc! [this alias] [this alias v])
  (dec! [this alias] [this alias v])
  (mark! [this alias] [this alias v])
  (update! [this alias v])
  (time-fn! [this alias f])
  (start! [this alias])
  (stop! [this alias]))

(def config->protocol
  (comp keyword
        str/lower-case
        (fnil name "tcp")
        :protocol))

(defmulti build-client config->protocol)

(defmethod build-client :udp
  [{:keys [host port] :or {host "127.0.0.1" port 5555}}]
  (RiemannClient/udp host (int port)))

(defmethod build-client :tcp
  [{:keys [host port] :or {host "127.0.0.1" port 5555}}]
  (RiemannClient/tcp host (int port)))

(defmethod build-client :tls
  [{:keys [host port tls] :or {host "127.0.0.1" port 5554}}]
  (RiemannClient/wrap
   (doto (new TcpTransport host (int port))
     (-> .-sslContext
         (.set (SSL/sslContext
                (:pkey tls)
                (:cert tls)
                (:authority tls)))))))

(defmethod build-client :default
  [_]
  (throw (ex-info "Cannot build riemann client for invalid protocol" {})))

(defn ^RiemannClient riemann-client
  "To keep dependency conflicts, let's use RiemannClient directly."
  [{:keys [batch] :as opts}]
  (try
    (let [client ^RiemannClient (build-client opts)]
      (try
        (.connect client)
        (catch Exception e
          (error e "cannot connect to riemann")))
      (if (and batch (number? batch) (pos? batch))
        (RiemannBatchClient. client (int batch))
        client))
    (catch Exception e
      (error e "cannot create riemann client"))))

(defn riemann-event!
  [^RiemannClient client defaults {:keys [host service time ^Double metric description] :as ev}]
  (let [tags  (some-> (concat (:tags defaults) (:tags ev)) set seq)
        attrs (merge (:attrs defaults) (:attrs ev))
        ttl   (if-let [t (or (:ttl ev) (:ttl defaults))] (float t))
        state (or (:state defaults) (:state ev))
        time  ^Long (or time (quot (System/currentTimeMillis) 1000))]
    (-> (.event client)
        (.host (or host (:host defaults) (raven/localhost)))
        (.service (or service (:service defaults) "<none>"))
        (.time (long time))
        (cond-> metric      (.metric metric)
                state       (.state ^String state)
                (seq attrs) (.attributes ^java.util.Map attrs)
                (seq tags)  (.tags ^java.util.List tags)
                ttl         (.ttl (float ttl))
                description (.description ^String description))
        (.send)
        (.deref 100 java.util.concurrent.TimeUnit/MILLISECONDS))))

(defn build-metrics-reporters
  [reg reporters rclient]
  (info "building metrics reporters")
  (vec
   (for [[k {:keys [opts interval]}] reporters]
     (case k
       :console (let [r (console/reporter reg opts)]
                  (info "building console reporter")
                  (reify
                    c/Lifecycle
                    (start [this] (console/start r interval) this)
                    (stop [this]
                      (info "scheduling a final console report")
                      (.report r)
                      (console/stop r)
                      this)))
       :jmx      (let [r (jmx/reporter reg opts)]
                  (info "building jmx reporter")
                   (reify
                     c/Lifecycle
                     (start [this] (jmx/start r) this)
                     (stop [this]  (jmx/stop r) this)))
       :graphite (let [r (graphite/reporter reg opts)]
                   (info "building graphite reporter")
                   (reify
                     c/Lifecycle
                     (start [this] (graphite/start r interval) this)
                     (stop [this]  (graphite/stop r) this)))
       :riemann   (if-not rclient
                    (throw (ex-info "need a valid riemann client to build reporter" {}))
                    (let [r (riemann/reporter rclient reg opts)]
                      (info "building riemann reporter")
                       (reify
                         c/Lifecycle
                         (start [this] (riemann/start r interval) this)
                         (stop [this]
                           (info "scheduling a final report of our metrics")
                           (.report ^ScheduledReporter r)
                           (riemann/stop r)
                           this))))
       (throw (ex-info "invalid metrics reporter" {}))))))

(defn build-metrics
  [{:keys [reporters]} rclient]
  (let [reg  (m/new-registry)
        reps (build-metrics-reporters reg reporters rclient)]
    (doseq [r reps]
      (c/start r))
    [reg reps]))

(defn ->alias
  [v]
  (if (sequential? v)
    (map name v)
    (name v)))

(defrecord Reporter [rclient raven reporters registry sentry metrics riemann prevent-capture?]
  c/Lifecycle
  (start [this]
    (let [rclient    (when riemann (riemann-client riemann))
          [reg reps] (build-metrics metrics rclient)
          raven      (when sentry (http/build-client (:http sentry)))]
      (when (and raven (not prevent-capture?))
        (with-uncaught e
          (capture! (assoc this :raven raven) e)))
      (assoc this
             :registry  reg
             :reporters reps
             :rclient   rclient
             :raven     raven)))
  (stop [this]
    (when-not prevent-capture?
      (with-uncaught e
        (error e "uncaught exception.")))
    (doseq [r reporters]
      (c/stop r))
    (when registry
      (info "shutting down metric registry")
      (m/remove-all-metrics registry))
    (when rclient
      (try
        (.close ^RiemannClient rclient)
        (catch Exception _)))
    (assoc this :raven nil :reporters nil :registry nil :rclient nil))
  MetricHolder
  (instrument! [this prefix]
    (when registry
      (doseq [[f title] [[jvm/register-jvm-attribute-gauge-set ["jvm" "attribute"]]
                         [jvm/register-memory-usage-gauge-set ["jvm" "memory"]]
                         [jvm/register-file-descriptor-ratio-gauge-set ["jvm" "file"]]
                         [jvm/register-garbage-collector-metric-set ["jvm" "gc"]]
                         [jvm/register-thread-state-gauge-set ["jvm" "thread"]]]]
        (f registry (mapv name (concat prefix title))))))
  (build! [this type alias f]
    (when registry
      (assert (fn? f))
      (case type
        :gauge (or (gge/gauge registry (->alias alias))
                   (gge/gauge-fn registry (->alias alias) f))
        (throw (ex-info "invalid metric type" {})))))
  (build! [this type alias]
    (when registry
      (case type
        :counter   (cnt/counter registry (->alias alias))
        :meter     (mtr/meter registry (->alias alias))
        :histogram (hst/histogram registry (->alias alias))
        :timer     (tmr/timer registry (->alias alias))
        (throw (ex-info "invalid metric type" {})))))
  (inc! [this alias]
    (when registry
      (cnt/inc! (cnt/counter registry (->alias alias)))))
  (inc! [this alias v]
    (when registry
      (cnt/inc! (cnt/counter registry (->alias alias)) v)))
  (dec! [this alias]
    (when registry
      (cnt/dec! (cnt/counter registry (->alias alias)))))
  (dec! [this alias v]
    (when registry
      (cnt/dec! (cnt/counter registry (->alias alias)) v)))
  (mark! [this alias]
    (when registry
      (mtr/mark! (mtr/meter registry (->alias alias)))))
  (mark! [this alias v]
    (when registry
      (mtr/mark! (mtr/meter registry (->alias alias)) v)))
  (update! [this alias v]
    (when registry
      (hst/update! (hst/histogram registry (->alias alias)) v)))
  (time-fn! [this alias f]
    (if registry
      (tmr/time-fn! (tmr/timer registry (->alias alias)) f)
      (f)))
  (start! [this alias]
    (when registry
      (tmr/start (tmr/timer registry (->alias alias)))))
  (stop! [this alias]
    (when registry
      (tmr/stop (tmr/timer registry (->alias alias)))))
  SentrySink
  (capture! [this e]
    (error e "captured exception")
    (when raven
      (try
        (raven/capture! raven (:dsn sentry) e)
        (catch Exception e
          (error e "could not send capture")))))
  RiemannSink
  (send! [this ev]
    (when rclient
      (riemann-event! rclient (:defaults riemann) ev))))

(defmacro time!
  [reporter alias & body]
  `(time-fn! ~reporter ~alias (fn [] (do ~@body))))

(defmacro with-time!
  [[reporter alias] & body]
  `(time-fn! ~reporter ~alias (fn [] (do ~@body))))

(defn make-reporter
  ([]
   (map->Reporter nil))
  ([reporter]
   (map->Reporter reporter)))

(extend-type nil
  MetricHolder
  (instrument! [this prefix])
  (build! ([this type alias f]) ([this type alias]))
  (inc! ([this alias]) ([this alias v]))
  (dec! ([this alias]) ([this alias v]))
  (mark! ([this alias]) ([this alias v]))
  (update! [this alias v])
  (time-fn! [this alias f] (f))
  (start! [this alias])
  (stop! [this alias])
  SentrySink
  (capture! [this e])
  RiemannSink
  (send! [this ev]))

(s/def ::bundle string?)
(s/def ::password string?)
(s/def ::ssl-bundle (s/keys :req-un [::bundle ::password]))
(s/def ::cert string?)
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

(s/def ::riemann (s/keys :req-un [::host] :opt-un [::port ::protocol ::batch ::defaults ::tls]))

(s/def ::opts map?)
(s/def ::interval pos-int?)
(s/def ::reporter-config (s/keys :req-un [] :opt-un [::interval ::opts]))
(s/def ::reporters (s/map-of #{:graphite :riemann :console :jmx} ::reporter-config))
(s/def ::metrics (s/keys :req-un [::reporters]))

(s/def ::http (s/keys :req-un [] :opt-un [::ssl ::disable-epoll ::logging ::loop-thread-count]))
(s/def ::sentry (s/keys :req-un [::dsn] :opt-un [::http]))
(s/def ::config (s/keys :req-un [] :opt-un [::prevent-capture? ::sentry ::metrics ::riemann]))
