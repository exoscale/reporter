(ns spootnik.reporter.reporter-test 
  (:require
   [clojure.test :refer [deftest is testing]]
   [spootnik.reporter :as sut]))

(deftest report-error-test
  (with-redefs [sut/capture! identity] 
    (testing "with message"
      (is (= {:message "error message" :extra {:foo "bar"}}
             (sut/report-error {:message "error message"}
                               {:foo "bar"}))))

    (testing "with exception"
      (let [e (ex-info "Exception" {:some :more-data}
                       (ex-info "Root exception" {:some :data}))]
        (is (= {:throwable e :extra {:foo "bar"}}
               (sut/report-error e
                                 {:foo "bar"})))))))

