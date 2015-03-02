(ns cmr.metadata-db.test.services.concept-service
  "Contains unit tests for service layer methods and associated utility methods."
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [cmr.metadata-db.services.util :as util]
            [cmr.metadata-db.services.concept-service :as cs]
            [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.data.memory-db :as memory]
            [cmr.metadata-db.services.messages :as messages]
            [cmr.common.dev.util :as du])
  (import clojure.lang.ExceptionInfo))


(def example-concept
  {:concept-id "C1000000000-PROV1"
   :concept-type :collection
   :native-id "provider collection id"
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"
   :revision-id 1
   :extra-fields {:entry-title "ET-1"
                  :entry-id "EID-1"}})

(deftest split-concept-id-revision-id-tuples-test
  (testing "one pair"
    (is (= {"PROV1" {:collection [["C10-PROV1" 0]]}}
           (cs/split-concept-id-revision-id-tuples [["C10-PROV1" 0]]))))
  (testing "multiple"
    (let [tuples [["C10-PROV1" 0]
                  ["G1-PROV1" 1]
                  ["G2-PROV1" 5]
                  ["C1-PROV2" 1]
                  ["C2-PROV2" 5]]
          expected {"PROV1" {:collection [["C10-PROV1" 0]]
                             :granule [["G1-PROV1" 1]
                                       ["G2-PROV1" 5]]}
                    "PROV2" {:collection [["C1-PROV2" 1]
                                          ["C2-PROV2" 5]]}}]
      (is (= expected (cs/split-concept-id-revision-id-tuples tuples))))))

;;; Verify that the revision id check works as expected.
(deftest check-concept-revision-id-test
  (let [previous-concept example-concept]
    (testing "valid revision-id"
      (let [db (memory/create-db [example-concept])
            concept (assoc previous-concept :revision-id 2)]
        (is (= {:status :pass} (cs/check-concept-revision-id db concept previous-concept)))))
    (testing "invalid revision-id - high"
      (let [db (memory/create-db [example-concept])
            concept (assoc previous-concept :revision-id 3)
            result (cs/check-concept-revision-id db concept previous-concept)]
        (is (= (:status result) :fail))
        (is (= (:expected result) 2))))
    (testing "invalid revision-id - low"
      (let [db (memory/create-db [example-concept])
            concept (assoc previous-concept :revision-id 0)
            result (cs/check-concept-revision-id db concept previous-concept)]
        (is (= (:status result) :fail))
        (is (= (:expected result) 2))))))

;;; Verify that the revision id validation works as expected.
(deftest validate-concept-revision-id-test
  (let [previous-concept example-concept]
    (testing "valid concept revision-id"
      (let [concept (assoc previous-concept :revision-id 2)]
        (cs/validate-concept-revision-id (memory/create-db [example-concept]) concept previous-concept)))
    (testing "invalid concept revision-id"
      (let [concept (assoc previous-concept :revision-id 3)]
        (is (thrown-with-msg?
              ExceptionInfo
              (du/message->regex (messages/invalid-revision-id (:concept-id concept) 2 3))
              (cs/validate-concept-revision-id (memory/create-db [example-concept]) concept previous-concept)))))
    (testing "missing concept-id no revision-id"
      (let [concept (dissoc previous-concept :concept-id)]
        (cs/validate-concept-revision-id (memory/create-db [example-concept]) concept previous-concept)))
    (testing "missing concept-id valid revision-id"
      (let [concept (-> previous-concept (dissoc :concept-id) (assoc :revision-id 1))]
        (cs/validate-concept-revision-id (memory/create-db [example-concept]) concept previous-concept)))
    (testing "missing concept-id invalid revision-id"
      (let [concept (-> previous-concept (dissoc :concept-id) (assoc :revision-id 2))]
        (is (thrown-with-msg?
              ExceptionInfo
              (du/message->regex (messages/invalid-revision-id (:concept-id concept) 1 2))
              (cs/validate-concept-revision-id (memory/create-db [example-concept]) concept previous-concept)))))))


;;; Verify that the try-to-save logic is correct.
(deftest try-to-save-test
  (testing "valid no revision-id"
    (let [db (memory/create-db [example-concept])
          result (cs/try-to-save db (dissoc example-concept :revision-id) nil)]
      (is (= (:revision-id result) 2))))
  (testing "valid with revision-id"
    (let [db (memory/create-db [example-concept])
          result (cs/try-to-save db (assoc example-concept :revision-id 2) 2)]
      (is (= (:revision-id result) 2))))
  (testing "invalid with low revision-id"
    (is (thrown-with-msg? ExceptionInfo (du/message->regex (messages/invalid-revision-id-unknown-expected 1))
                          (cs/try-to-save (memory/create-db [example-concept]) (assoc example-concept :revision-id 1) 1)))))



