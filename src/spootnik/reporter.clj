(ns spootnik.reporter
  (:require [com.stuartsierra.component :as c]
            [clojure.tools.logging :as log]
            [raven.client :as raven]
            [spootnik.reporter.impl :as rptr]))

(def reporter
  "The main reporter instance"
  nil)

(defn inner-start
  [_ config]
  (c/start
   (rptr/map->Reporter config)))

(defn initialize!
  "Initialize the root binding in this namespace to a reporter
   component using the provided configuration."
  [config]
  (alter-var-root #'reporter inner-start config))

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
(def build!      #(apply rptr/build! reporter %&))
(def inc!        #(apply rptr/inc! reporter %&))
(def dec!        #(apply rptr/dec! reporter %&))
(def mark!       #(apply rptr/mark! reporter %&))

(defmacro with-time!
  [alias & body]
  `(time-fn! ~alias (fn [] (do ~@body))))

(defn report-error
  "Log and report an error. Extra is a map of extra data."
  [error extra]
  (try
    (log/error error)
    (capture! (-> (if (instance? Exception error)
                    (-> {:data (ex-data error)}
                        (raven/add-exception! error))
                    {:message error})
                  (raven/add-extra! extra)))
    (catch Exception e
      (log/error e "Sentry failure"))))
