(ns spootnik.reporter.impl
  (:require [aleph.http :as http]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as c]
            [metrics.reporters.console :as console]
            [metrics.reporters.jmx :as jmx]
            [metrics.reporters.graphite :as graphite]
            [metrics.reporters.riemann :as riemann]
            [spootnik.log-reporter :as logs]
            [metrics.jvm.core :as jvm]
            [metrics.core :as m]
            [metrics.gauges :as gge]
            [metrics.counters :as cnt]
            [metrics.meters :as mtr]
            [metrics.timers :as tmr]
            [metrics.histograms :as hst]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clojure.tools.logging :refer [info error]]
            [spootnik.uncaught :refer [with-uncaught]]
            [spootnik.reporter.sentry :as rs])
  (:import com.aphyr.riemann.client.RiemannClient
           com.aphyr.riemann.client.RiemannBatchClient
           com.aphyr.riemann.client.TcpTransport
           com.aphyr.riemann.client.EventDSL
           com.aphyr.riemann.client.IPromise
           com.codahale.metrics.ScheduledReporter
           io.netty.handler.ssl.ClientAuth
           io.netty.handler.ssl.SslContextBuilder
           io.prometheus.client.CollectorRegistry
           io.prometheus.client.dropwizard.DropwizardExports
           io.prometheus.client.exporter.common.TextFormat
           io.prometheus.client.hotspot.DefaultExports
           io.prometheus.client.Gauge
           io.prometheus.client.Counter
           io.prometheus.client.Gauge$Child
           io.prometheus.client.Counter$Child
           io.prometheus.client.SimpleCollector$Builder
           io.prometheus.client.Collector
           io.prometheus.client.exporter.PushGateway
           io.prometheus.client.exporter.HttpConnectionFactory
           java.io.File
           java.io.FileInputStream
           java.io.InputStream
           java.io.StringWriter
           java.util.concurrent.TimeUnit
           java.util.List
           java.util.Map
           java.net.InetSocketAddress
           java.net.URL
           java.security.cert.X509Certificate
           java.security.cert.CertificateFactory
           javax.net.ssl.HttpsURLConnection
           io.netty.handler.ssl.JdkSslContext
           javax.net.ssl.SSLContext
           [io.opentelemetry.api GlobalOpenTelemetry OpenTelemetry]
           [io.opentelemetry.api.common AttributeKey Attributes]
           [io.opentelemetry.api.metrics DoubleGauge LongCounter ObservableLongMeasurement]
           [io.opentelemetry.exporter.otlp.metrics OtlpGrpcMetricExporter]
           [io.opentelemetry.sdk OpenTelemetrySdk]
           [io.opentelemetry.sdk.metrics SdkMeterProvider]
           [io.opentelemetry.api.metrics MeterProvider]
           [io.opentelemetry.sdk.metrics.export PeriodicMetricReader]
           [io.opentelemetry.sdk.resources Resource]
           [java.util.function Consumer]))

(defprotocol RiemannSink
  (send! [this e]))

(defprotocol SentrySink
  (capture! [this e] [this e tags]))

(defprotocol PushGatewaySink
  (counter! [this metric])
  (gauge! [this metric])
  (push-metrics! [this]))

(defprotocol MetricHolder
  (instrument! [this prefix])
  (build! [this type alias] [this type alias f])
  (inc! [this alias] [this alias v])
  (dec! [this alias] [this alias v])
  (mark! [this alias] [this alias v])
  (update! [this alias v])
  (time-fn! [this alias f])
  (start! [this alias])
  (stop! [this ctx]))

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

(declare client-ssl-context)

(defmethod build-client :tls
  [{:keys [host port tls] :or {host "127.0.0.1" port 5554}}]
  (RiemannClient/wrap
   (doto (new TcpTransport host (int port))
     (-> .-sslContext
         (.set (.context (client-ssl-context tls)))))))

(defmethod build-client :default
  [_]
  (throw (ex-info "Cannot build riemann client for invalid protocol" {})))

(defn build-console-metrics-reporter [reg {:keys [opts interval]}]
  (let [r (console/reporter reg opts)]
    (reify
      c/Lifecycle
      (start [this] (console/start r interval) this)
      (stop [this]
        (info "scheduling a final console report")
        (.report r)
        (console/stop r)
        this))))

(defn build-logs-metrics-reporter [reg {:keys [opts interval]}]
  (let [r (logs/reporter reg opts)]
    (reify
      c/Lifecycle
      (start [this] (logs/start r interval) this)
      (stop [this]
        (info "scheduling a final console report")
        (.report r)
        (logs/stop r)
        this))))

(defn build-jmx-metrics-reporter [reg {:keys [opts]}]
  (let [r (jmx/reporter reg opts)]
    (reify
      c/Lifecycle
      (start [this] (jmx/start r) this)
      (stop [this]  (jmx/stop r) this))))

(defn build-graphite-metrics-reporter [reg {:keys [opts interval]}]
  (let [r (graphite/reporter reg opts)]
    (reify
      c/Lifecycle
      (start [this] (graphite/start r interval) this)
      (stop [this]  (graphite/stop r) this))))

(defn build-riemann-metrics-reporter [reg rclient {:keys [opts interval]}]
  (if-not rclient
    (throw (ex-info "need a valid riemann client to build reporter" {}))
    (let [r (riemann/reporter rclient reg opts)]
      (reify
        c/Lifecycle
        (start [this] (riemann/start r interval) this)
        (stop [this]
          (info "scheduling a final report of our metrics")
          (.report ^ScheduledReporter r)
          (riemann/stop r)
          this)))))

(defn build-prometheus-metrics-reporter [reg ^CollectorRegistry registry]
  (reify
    c/Lifecycle
    (start [this]
      (info "start prometheus reporter")
      (let [exporter (DropwizardExports. reg)]
        (.register registry exporter)
        (DefaultExports/initialize)
        this))
    (stop [this])))

(defn build-pushgateway-metrics-reporter [^CollectorRegistry registry]
  (reify
    c/Lifecycle
    (start [this]
      (info "Starting pushgateway reporter")
      this)
    (stop [this]
      (info "Clearing pushgateway registry")
      (.clear registry))))

(defn build-otel-metrics-reporter [reg {:keys [job endpoint]} {:keys [opts]}]
  (reify
    c/Lifecycle
    (start [this] this)
    (stop [this] this)))

(defn build-metrics-reporter [reg rclient otel ^CollectorRegistry prometheus-registry ^CollectorRegistry pushgateway-registry [type opt]]
  (condp = type
    :console  (build-console-metrics-reporter reg opt)
    :logs     (build-logs-metrics-reporter reg opt)
    :jmx      (build-jmx-metrics-reporter  reg opt)
    :graphite (build-graphite-metrics-reporter reg opt)
    :riemann  (build-riemann-metrics-reporter reg rclient opt)
    :prometheus (build-prometheus-metrics-reporter reg prometheus-registry)
    :pushgateway (build-pushgateway-metrics-reporter pushgateway-registry)
    :otel (build-otel-metrics-reporter reg otel opt)
    :else (throw (ex-info "Cannot build requested metrics reporter" type))))

(defn riemann-client
  "To keep dependency conflicts, let's use RiemannClient directly."
  ^RiemannClient
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

(defn ->event [^RiemannClient client defaults {:keys [host service time ^Double metric description] :as ev}]
  (let [tags  (some-> (concat (:tags defaults) (:tags ev)) set seq)
        attrs (merge (:attrs defaults) (:attrs ev))
        ttl   (when-let [t (or (:ttl ev) (:ttl defaults))] (float t))
        state (or (:state ev) (:state defaults))
        time  ^Long (or time (quot (System/currentTimeMillis) 1000))]
    (-> (.event client)
        (.host (or host (:host defaults) (rs/localhost)))
        (.service (or service (:service defaults) "<none>"))
        (.time (long time))
        (cond-> metric      (.metric metric)
                state       (.state ^String state)
                (seq attrs) (.attributes ^Map attrs)
                (seq tags)  (.tags ^List tags)
                ttl         (.ttl (float ttl))
                description (.description ^String description))
        ^EventDSL
        (.build))))

(defn riemann-send [^RiemannClient client events]
  (if (= (count events) 1)
    (.sendEvent client (first events))
    (.sendEvents client ^List events)))

(defn riemann-events!
  [client defaults events]
  (let [deref' #(.deref ^IPromise % 100 TimeUnit/MILLISECONDS)]
    (->> events
         (map #(->event client defaults %))
         (riemann-send client)
         (deref'))))

(defn build-metrics-reporters
  [reg reporters rclient otel ^CollectorRegistry prometheus-registry pushgateway-registry]
  (info "building metrics reporters")
  (mapv (partial build-metrics-reporter reg rclient otel prometheus-registry pushgateway-registry) reporters))

(defn build-metrics
  [{:keys [reporters]} rclient otel ^CollectorRegistry prometheus-registry pushgateway-registry]
  (let [reg  (m/new-registry)
        reps (build-metrics-reporters reg reporters rclient otel prometheus-registry pushgateway-registry)]
    (doseq [r reps]
      (c/start r))
    [reg reps]))

(defn ->alias
  [v]
  (if (sequential? v)
    (map name v)
    (name v)))

(defn prometheus-str-metrics
  "Extracts and returns as string metrics from a Prometheus registry.
  Defaults to the defaultRegistry if no registry is provided."
  ([]
   (prometheus-str-metrics CollectorRegistry/defaultRegistry))
  ([^CollectorRegistry registry]
   (let [writer (StringWriter.)]
     (TextFormat/write004
      writer
      (.metricFamilySamples registry))
     (.toString writer))))

(defn prometheus-handler
  [prometheus ^CollectorRegistry prometheus-registry req]
  (if (and (:endpoint prometheus)
           (not= (:uri req) (:endpoint prometheus)))
    {:status 404}
    {:status 200
     :headers {"Content-Type" TextFormat/CONTENT_TYPE_004}
     :body (prometheus-str-metrics prometheus-registry)}))

(defn ^"[Ljava.security.cert.X509Certificate;" read-ca-certs
  "Read all available certificate from the default UNIX location
   Adds any provided certificate as CA.
   Yields an array of X509Certificate, ready to be used to
   build a `trustManager` for an `SslContext`"
  [files]
  (let [->file #(if (string? %) (io/file %) %)
        dir    (io/file "/etc/ssl/certs")
        fact   (CertificateFactory/getInstance "X509")]
    (->> dir
         (.listFiles)
         (seq)
         (concat (map ->file files))
         (filter #(.isFile ^File %))
         (map #(FileInputStream. ^File %))
         (map #(.generateCertificates fact ^InputStream %))
         (into [])
         (apply concat)
         (into-array X509Certificate))))

(defn server-ssl-context
  [{:keys [pkey cert ca-cert]}]
  (-> (SslContextBuilder/forServer (io/file cert) (io/file pkey))
      (.trustManager (io/file ca-cert))
      (.clientAuth ClientAuth/REQUIRE)
      (.build)))

(defn client-ssl-context
  ^JdkSslContext
  [{:keys [pkey cert ca-cert authorities]}]
  (let [authorities (or authorities [ca-cert])
        ca-certs    (read-ca-certs authorities)]
    (-> (SslContextBuilder/forClient)
        (.trustManager ca-certs)
        (.keyManager (io/file cert) (io/file pkey))
        (.build))))

(defn- https-connection-factory
  [tls]
  (let [ssl-context (client-ssl-context tls)]
    (reify HttpConnectionFactory
      (create [this url]
        (doto ^HttpsURLConnection (.openConnection (URL. url))
          (.setSSLSocketFactory  (.getSocketFactory ^SSLContext (.context ssl-context))))))))

(defn collector-builder-of
  ^SimpleCollector$Builder [type]
  (condp = type
    :gauge (Gauge/build)
    :counter (Counter/build)))

(defn build-pushgateway-collector
  ^SimpleCollector$Builder [{:keys [name type  help  label-names]}]
  (-> (collector-builder-of type)
      (.name (csk/->snake_case_string name))
      (.help help)
      (.labelNames (into-array String (map csk/->snake_case_string label-names)))))

(defn register-pushgateway-collector
  ^Collector
  [^SimpleCollector$Builder collector registry]
  (.register collector registry))

(defn build-pushgateway-client
  [{:keys [host port tls]
    :or {port 9091}}]
  (let [protocol (if tls "https" "http")
        url (URL. protocol host port "")
        client (PushGateway. url)]
    (when tls
      (.setConnectionFactory client (https-connection-factory tls)))
    client))

(defn build-collectors! [registry metrics]
  (into {} (map (fn [metric] [(:name metric) (-> (build-pushgateway-collector metric)
                                                 (register-pushgateway-collector registry))])
                metrics)))

;; these can work with Pushgateway Gauge/Counter or open telemetry
(defmulti set-gauge! "Set the given `gauge` with the opts"
          (fn [gauge opts]
            (cond
              (map? gauge) :otel
              (instance? io.prometheus.client.SimpleCollector gauge) :prometheus)))
(defmulti inc-counter! "Increment the given `counter` with the opts"
          (fn [counter opts]
            (cond
              (map? counter) :otel
              (instance? io.prometheus.client.SimpleCollector counter) :prometheus)))

;; pushgateway
(defmethod set-gauge! :prometheus
  [^Gauge gauge {:keys [label-values value]}]
  (-> gauge
      ^Gauge$Child (.labels (into-array String (map name label-values)))
      (.set ^double value)))
(defmethod inc-counter! :prometheus
  [^Counter counter {:keys [value label-values] :or {value 1}}]
  (-> counter
      ^Counter$Child (.labels (into-array String (map name label-values)))
      (.inc (double value))))

;; open telemetry
(defn- ^Attributes arr->raw-attrs [kvs-arr]
  (let [builder (Attributes/builder)]
    (doseq [[k v] (partition 2 kvs-arr)]
      (.put builder ^String (name k) ^String (name v)))
    (.build builder)))
(defn- ^Attributes arr->attrs [kvs-arr]
  (->> (apply hash-map kvs-arr)
       (reduce-kv (fn [m k v]
                    (cond
                      (= "job" (name k))
                      (do
                        (log/warn "'job' attribute for OTEL metric will be dropped, it is derived from OTEL 'service.name' ")
                        m)
                      (= "instance" (name k))
                      (do
                        (log/warn "'instance' for OTEL metric will be dropped, it is derived from OTEL 'service.instance.id' ")
                        m)
                      :else (assoc m k v))) {})
       (apply concat)
       (arr->raw-attrs)))

(defmethod set-gauge! :otel
  [{:keys [metric label-names grouping-keys]} {:keys [label-values value]}]
  ;; TODO ignore :instance
  (.set ^DoubleGauge metric (double value) (arr->attrs (concat (interleave label-names label-values)
                                                               (mapcat identity grouping-keys)))))
(defmethod inc-counter! :otel
  ;; TODO ignore :instance
  [{:keys [metric label-names grouping-keys]} {:keys [label-values value] :or {value 1}}]
  (.add ^LongCounter metric (long value) (arr->attrs (concat (interleave label-names label-values)
                                                             (mapcat identity grouping-keys)))))

(defn push-pushgateway-metric!
  [^PushGateway pg ^CollectorRegistry registry ^String job ^java.util.Map grouping-keys]
  (.push pg registry job grouping-keys))

(defn flush-otel-metrics! [{:keys [^MeterProvider provider]}]
  ;; flushes all metric readers
  ;; if we initialized the sdk, then we can flush
  ;; otherwise we dont control it
  (if (instance? SdkMeterProvider provider)
    (-> (.forceFlush ^SdkMeterProvider provider)
        (.join 10 TimeUnit/SECONDS))
    (log/warnf "Cannot flush metrics: provider is not of class SdkMeterProvider: %s" (class provider))))

(defn parse-pggrouping-keys [grouping-keys]
  (into {} (for [[k v] grouping-keys] [(csk/->snake_case_string k) v])))

(defn build-otel-metrics [{:keys [endpoint job instance grouping-keys initialize-sdk?]} metrics-def]
  ;; https://opentelemetry.io/docs/specs/otel/compatibility/prometheus_and_openmetrics/
  (let [provider (if initialize-sdk?
                   (let [attrs    (arr->raw-attrs ["service.name" job "service.instance.id" instance])
                         resource (-> (Resource/getDefault)
                                      (.merge (Resource/create attrs)))
                         exporter (-> (OtlpGrpcMetricExporter/builder)
                                      (.setEndpoint endpoint)
                                      (.setTimeout 2 TimeUnit/SECONDS)
                                      (.build))]
                     (-> (SdkMeterProvider/builder)
                         (.setResource resource)
                         (.registerMetricReader (PeriodicMetricReader/create exporter))
                         (.build)))
                   ;; else
                   (.getMeterProvider (GlobalOpenTelemetry/get)))
        mbuilder (.build (.meterBuilder provider "exoscale.reporter"))
        make-fn  (fn [{:keys [name help type label-names]}]
                   (condp = type
                     :gauge (-> mbuilder
                                (.gaugeBuilder (clojure.core/name name))
                                (.setDescription help)
                                (.build))
                     :counter (-> mbuilder
                                  (.counterBuilder (clojure.core/name name))
                                  (.setDescription help)
                                  (.build))))
        metrics  (reduce (fn [acc m]
                           (assoc acc (:name m) {:metric (make-fn m)
                                                 :label-names (:label-names m)
                                                 :grouping-keys (parse-pggrouping-keys grouping-keys)}))
                         {}
                         metrics-def)]
    (when initialize-sdk?
      (-> (OpenTelemetrySdk/builder)
          (.setMeterProvider provider)
          (.buildAndRegisterGlobal)))
    {:provider provider
     :metrics metrics}))

;; Reporter configuration specs:
;; https://github.com/exoscale/reporter/blob/master/src/spootnik/reporter/specs.clj

(defrecord Reporter [rclient sentry-options reporters registry sentry
                     metrics riemann prevent-capture? prometheus
                     started? pushgateway
                     otel]
  c/Lifecycle
  (start [this]
    (if started?
      this
      (let [prometheus-registry  (CollectorRegistry/defaultRegistry)
            [pgclient pgjob pgregistry pggrouping-keys] (when pushgateway [(build-pushgateway-client pushgateway)
                                                                           (name (:job pushgateway))
                                                                           (CollectorRegistry.)
                                                                           (parse-pggrouping-keys (:grouping-keys pushgateway))])
            pgmetrics            (when pushgateway (build-collectors! pgregistry (get-in metrics [:reporters :pushgateway])))
            rclient              (when riemann (riemann-client riemann))
            [reg reps]           (build-metrics metrics rclient otel prometheus-registry pgregistry)
            otel                 (when otel (build-otel-metrics otel (get-in metrics [:reporters :otel])))
            options              (when sentry (or sentry-options {}))
            prometheus-server    (when prometheus
                                   (let [tls (:tls prometheus)
                                         opts (cond-> {:port (:port prometheus)}
                                                (some? (:tls prometheus))
                                                (assoc :ssl-context (server-ssl-context tls))
                                                (:host prometheus)
                                                (assoc :socket-address
                                                       (InetSocketAddress.
                                                        ^String (:host prometheus)
                                                        ^int (:port prometheus))))]
                                     (http/start-server
                                      (partial prometheus-handler
                                               prometheus
                                               prometheus-registry)
                                      opts)))]

        (rs/init! sentry)

        (when-not prevent-capture?
          (with-uncaught e
            (capture! (assoc this :sentry-options options) e)))
        (cond-> (assoc this
                       :registry      reg
                       :reporters     reps
                       :rclient       rclient
                       :sentry-options options
                       :started? true)
          prometheus (assoc :prometheus {:server   prometheus-server
                                         :registry prometheus-registry})
          pushgateway (assoc :pushgateway {:client pgclient
                                           :registry pgregistry
                                           :grouping-keys pggrouping-keys
                                           :metrics pgmetrics
                                           :job pgjob})
          otel (assoc :otel otel)))))
  (stop [this]
    (when started?
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
      (when prometheus
        (.close ^java.io.Closeable (:server prometheus)))
      (when otel
        (let [{:keys [^MeterProvider provider]} otel]
          (if (instance? SdkMeterProvider provider)
            (-> (.shutdown ^SdkMeterProvider provider)
                (.join 10 TimeUnit/SECONDS))
            (log/warnf "Shutdown not available: provider is not of class SdkMeterProvider: %s" (class provider)))))

      (rs/close! sentry))

    (assoc this
           :sentry-options nil
           :reporters nil
           :registry nil
           :rclient nil
           :prometheus nil
           :pushgateway nil
           :otel nil
           :started? false))
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
  (stop! [this ctx]
    (when registry
      (tmr/stop ctx)))
  PushGatewaySink
  (counter! [this {:keys [name push?] :or {push? true} :as metric}]
    (when pushgateway
      (let [{:keys [client metrics job registry grouping-keys]} pushgateway
            collector (name metrics)]
        (inc-counter! collector metric)
        (when push?
          (push-pushgateway-metric! client registry job grouping-keys))))
    (when otel
      (let [{:keys [^MeterProvider provider metrics]} otel
            impl (name metrics)]
        (inc-counter! impl metric)
        (when push?
          (flush-otel-metrics! otel)))))

  (gauge! [this {:keys [name push?] :or {push? true}  :as metric}]
    (when pushgateway
      (let [{:keys [client metrics job registry grouping-keys]} pushgateway
            collector (name metrics)]
        (set-gauge! collector metric)
        (when push?
          (push-pushgateway-metric! client registry job grouping-keys))))
    (when otel
      (let [{:keys [^MeterProvider provider metrics]} otel
            impl (name metrics)]
        (set-gauge! impl metric)
        (when push?
          (flush-otel-metrics! otel)))))
  (push-metrics! [this]
    (when pushgateway
      (let [{:keys [client job registry grouping-keys]} pushgateway]
        (push-pushgateway-metric! client registry job grouping-keys)))
    (when otel
      (flush-otel-metrics! otel)))
  SentrySink
  (capture! [this e]
    (capture! this e {}))
  (capture! [_this e tags]
    (rs/send-event! sentry sentry-options e tags))
  RiemannSink
  (send! [_this ev]
    (when rclient
      (let [to-seq #(if-not (sequential? %) [%] %)]
        (->> ev
             to-seq
             (riemann-events! rclient (:defaults riemann)))))))

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
  (stop! [this ctx])
  PushGatewaySink
  (gauge!   ([this metric]))
  (counter! ([this metric]))
  (push-metrics! ([this]))
  SentrySink
  (capture! ([this e]) ([this e tags]))
  RiemannSink
  (send! [this ev]))
