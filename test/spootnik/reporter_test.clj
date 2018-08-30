(ns spootnik.reporter-test
  (:require [clojure.test :refer :all]
            [spootnik.reporter :refer :all]
            [com.stuartsierra.component :as component]
            [raven.client :refer [http-requests-payload-stub]]))

(deftest timers-do-not-modify-the-world
  (let [reporter (component/start (map->Reporter {:metrics {:reporters {:console {:interval 100}}}}))]

    (is (= (time! reporter :one (+ 1 2))
           (time! nil :one (+ 1 2))))

    (is (= (time! reporter :two (do (Thread/sleep 200) "foo"))
           (time! nil :two "foo")))

    (component/stop reporter)))

(deftest sentry-sends-events
  (testing "we can send events to sentry using the :memory: backend"
    (let [reporter (component/start (map->Reporter {:sentry {:dsn ":memory:"}
                                                    :metrics {:reporters {:console {:interval 100}}}}))]

      (.capture! ^spootnik.reporter.SentrySink reporter {:message "A simple test event"})
      (is (= "A simple test event" (:message (first @http-requests-payload-stub))))

      (component/stop reporter))))
