(ns spootnik.reporter.sentry
  (:require
   [sentry-clj.core :as sentry-io]
   [manifold.deferred  :as d]
   [clojure.string :as str]
   [clojure.tools.logging :refer [error]]
   [clojure.java.shell :as sh]))

(def http-requests-payload-stub
  "Storage for stubbed http sentry events "
  (atom nil))

(defn in-memory? [dsn]
  (or (= dsn ":memory:")
      (nil? dsn)))

(def hostname-refresh-interval
  "How often to allow reading /etc/hostname, in seconds."
  60)

(defn get-hostname
  "Get the current hostname by shelling out to 'hostname'"
  []
  (or
   (try
     (let [{:keys [exit out]} (sh/sh "hostname")]
       (when (= exit 0)
         (str/trim out)))
     (catch Exception _))
   "<unknown>"))

(defn hostname
  "Fetches the hostname by shelling to 'hostname', whenever the given age
  is stale enough. If the given age is recent, as defined by
  hostname-refresh-interval, returns age and val instead."
  [[age val]]
  (if (and val (<= (* 1000 hostname-refresh-interval)
                   (- (System/currentTimeMillis) age)))
    [age val]
    [(System/currentTimeMillis) (get-hostname)]))

(let [cache (atom [nil nil])]
  (defn localhost
    "Returns the local host name."
    []
    (if (re-find #"^Windows" (System/getProperty "os.name"))
      (or (System/getenv "COMPUTERNAME") "localhost")
      (or (System/getenv "HOSTNAME")
          (second (swap! cache hostname))))))

(defn- payload->sentry-request [payload]
  {:url          (-> payload :uri)
   :method       (-> payload :request-method name clojure.string/upper-case)
   :query-string (-> payload :query-string)
   :headers      (-> payload :headers)})

(defn e->sentry-event [e options tags]
  (let [{:keys [message extra throwable]} e
        message      (or message (ex-message e))
        user         (some-> extra :org/uuid str)
        fingerprints (some-> options :fingerpint seq)
        request      (some-> extra :payload payload->sentry-request)]

    (cond-> {:message message
             :level :error
             :platform "java"
             :server-name  (localhost)}

      throwable    (assoc :throwable throwable)
      extra        (assoc :extra extra)
      (seq tags)   (assoc :tags tags)
      user         (assoc :user user)
      fingerprints (assoc :fingerprints fingerprints)
      request      (assoc :request request))))

(defn send-event! [dsn options e tags]
  (let [event (e->sentry-event e options tags)]
    (if-not (in-memory? dsn)
      (-> (try
            (sentry-io/send-event event)
            (catch Exception e
              (d/error-deferred e)))

          (d/chain
           (fn [event-id]
             (error e (str "captured exception as sentry event: " event-id))
             event-id))

          (d/catch (fn [e']
                     (error e "Failed to capture exception" {:tags tags :capture-exception e'})
                     (send-event! dsn options e' tags))))

      (swap! http-requests-payload-stub conj event))))

(defn init! [{:keys [dsn] :as sentry}]
  (if-not (in-memory? dsn)
    (sentry-io/init! dsn sentry)
    (reset! http-requests-payload-stub [])))

(defn close! [{:keys [dsn]}]
  (if-not (in-memory? dsn)
    (sentry-io/close!)
    (reset! http-requests-payload-stub nil)))
