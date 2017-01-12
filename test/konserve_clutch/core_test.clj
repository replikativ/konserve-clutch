(ns konserve-clutch.core-test
  (:require [clojure.test :refer :all]
            [konserve.core :as k]
            [konserve-clutch.core :refer :all]
            [clojure.core.async :refer [<!!]]))


(deftest clutch-store-test
  (testing "Test the couchdb store functionality."
    (let [store (<!! (new-clutch-store "couchdb-test-store"))]
      (<!! (k/assoc-in store [:foo] nil))
      (is (= (<!! (k/get-in store [:foo]))
             nil))
      (<!! (k/assoc-in store [:foo] :bar))
      (is (= (<!! (k/get-in store [:foo]))
             :bar))
      (<!! (k/dissoc store :foo))
      (is (= (<!! (k/get-in store [:foo]))
             nil))
      (<!! (k/bassoc store :binbar (byte-array (range 10))))
      (<!! (k/bget store :binbar (fn [{:keys [input-stream]}]
                                   (is (= (map byte (slurp input-stream))
                                          (range 10)))))))))


(comment

  (def store (<!! (new-clutch-store "couchdb-test-store")))

  (<!! (k/assoc-in store [:foo] nil))

  (<!! (k/get-in store [:foo]))

  (<!! (k/assoc-in store [:foo] :bar))

  (run-tests))
