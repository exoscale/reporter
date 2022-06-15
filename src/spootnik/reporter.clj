(ns spootnik.reporter
  (:require [com.stuartsierra.component :as c]
            [clojure.tools.logging :as log]
            [raven.client :as raven]
            [spootnik.reporter.impl :as rptr]
            [manifold.deferred :as d]))

(def ^:redef reporter
  "The main reporter instance"
  nil)

(defn- inner-start
  [_ config]
  (c/start
   (rptr/map->Reporter config)))

(defn- inner-stop
  [val]
  (when (some? val)
    (c/stop val)))

(defn initialize!
  "Initialize the root binding in this namespace to a reporter
   component using the provided configuration."
  [config]
  (alter-var-root #'reporter inner-start config))

(defn decommission!
  "Stop the reporter attached to the namespace's root binding."
  []
  (alter-var-root #'reporter inner-stop))

;;
;; Forward reporter functions, providing the current component
;;
(def instrument! #(apply rptr/instrument! reporter %&))
(def update!     #(apply rptr/update! reporter %&))
(def time-fn!    #(apply rptr/time-fn! reporter %&))
(def start!      #(apply rptr/start! reporter %&))
(def stop!       #(apply rptr/stop! reporter %&))
(def capture!    #(apply rptr/capture! reporter %&))
(def send!       #(apply rptr/send! reporter %&))
(def gauge!      #(apply rptr/gauge! reporter %&))
(def counter!    #(apply rptr/counter! reporter %&))
(def build!      #(apply rptr/build! reporter %&))
(def inc!        #(apply rptr/inc! reporter %&))
(def dec!        #(apply rptr/dec! reporter %&))
(def mark!       #(apply rptr/mark! reporter %&))

(defmacro with-time!
  "Wraps a body with a timer, using `alias` for the name."
  [alias & body]
  `(let [start# (start! ~alias)]
     (try
       ;; if ~@body is not deferred
       ;; the timer will stop on the 2nd if branch OR on the catch
       ;; we can't use a finally block for stopping the timer
       ;; because at that point we don't know if its a deferred or not (We would be stopping deferred timer before the end)
       ;; if it's a deferred:
       ;; > exception happened on deferred creation, it is stopped on catch
       ;; > exception or success happened, it will be stopped on the callbacks
       (let [out# (do ~@body)]
         (if (d/deferred? out#)
           (d/finally out# #(stop! start#))
           (do
             (stop! start#)
             out#)))
       ;; time-fn has a try/finally which we can't use because of deferred
       ;; so we emulate it by catching Throwable...
       (catch Throwable t#
         (stop! start#)
         (throw t#)))))

(defn report-error
  "Log and report an error. Extra is a map of extra data."

  ([error]
   (report-error error {}))

  ([error extra]
   (log/error error)
   (-> (capture! (-> (if (instance? Exception error)
                       (-> {:data (ex-data error)}
                           (raven/add-exception! error))
                       {:message error})
                     (raven/add-extra! extra)))
       (d/catch Throwable
           (fn [e]
             (log/error e "Sentry failure")))
       (try
         (catch Throwable e
           (log/error e "Sentry failure"))))))
