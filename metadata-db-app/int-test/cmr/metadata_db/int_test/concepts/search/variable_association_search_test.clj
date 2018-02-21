(ns cmr.metadata-db.int-test.concepts.search.variable-association-search-test
  "Contains integration tests for searching variable associations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   ;; It's like it doesn't know about the multi-method definitions
   [cmr.metadata-db.int-test.concepts.utils.variable-association :as variable-association]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-variable-associations
  (let [coll1 (concepts/create-and-save-concept
               :collection "REG_PROV" 1 1 {:extra-fields {:entry-id "entry-1"
                                                          :entry-title "et1"
                                                          :version-id "v1"
                                                          :short-name "s1"}})
        coll2 (concepts/create-and-save-concept
               :collection "REG_PROV" 2 1 {:extra-fields {:entry-id "entry-2"
                                                          :entry-title "et2"
                                                          :version-id "v1"
                                                          :short-name "s2"}})
        associated-variable (concepts/create-and-save-concept :variable "REG_PROV" 1)
        var-association1 (concepts/create-and-save-concept
                          :variable-association coll1 associated-variable 1 3)
        var-association2 (concepts/create-and-save-concept
                          :variable-association coll2 associated-variable 2 2)]
    (testing "find latest revisions"
      (are3 [variable-associations params]
        (is (= (set variable-associations)
               (set (->> (util/find-latest-concepts :variable-association params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id))))))

        "by associated-concept-id"
        [var-association1]
        {:associated-concept-id "C1200000000-REG_PROV"}

        "with metadata"
        [var-association1 var-association2]
        {}

        "exclude metadata"
        [(dissoc var-association1 :metadata) (dissoc var-association2 :metadata)]
        {:exclude-metadata true}))

    (testing "find all revisions"
      (let [num-of-variable-associations (-> (util/find-concepts :variable-association {})
                                             :concepts
                                             count)]
        (is (= 5 num-of-variable-associations))))))

(deftest find-variable-associations-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [variable-association] with parameters [provider-id] is not supported."]}
           (util/find-concepts :variable-association {:provider-id "REG_PROV"})))))
