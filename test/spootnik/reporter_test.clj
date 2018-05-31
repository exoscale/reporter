(ns spootnik.reporter-test
  (:require [clojure.test :refer :all]
            [spootnik.reporter :refer :all]
            [com.stuartsierra.component :as component]))

(deftest timers-do-not-modify-the-world
  (let [reporter (component/start (map->Reporter {:metrics {:reporters {:console {:interval 100}}}}))]

    (is (= (time! reporter :one (+ 1 2))
           (time! nil :one (+ 1 2))))

    (is (= (time! reporter :two (do (Thread/sleep 200) "foo"))
           (time! nil :two "foo")))

    (component/stop reporter)))
