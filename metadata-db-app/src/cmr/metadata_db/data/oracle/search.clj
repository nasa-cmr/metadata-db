(ns cmr.metadata-db.data.oracle.search
"Provides implementations of the cmr.metadata-db.data.concepts/ConceptStore protocol methods
for retrieving concepts using parameters"
(:require [cmr.metadata-db.data.concepts :as c]
          [cmr.metadata-db.data.oracle.concepts :as oc]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.oracle.sql-helper :as sh]
            [cmr.metadata-db.data.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore))

(defn- find-batch-starting-id
  "Returns the first id that would be returned in a batch."
  ([conn table params]
   (find-batch-starting-id conn table params 0))
  ([conn table params min-id]
   (let [existing-params (when (seq params) (sh/find-params->sql-clause params))
         params-clause (if existing-params
                         `(and (>= :id ~min-id)
                               ~existing-params)
                         `(>= :id ~min-id))
         stmt (select ['(min :id)]
                      (from table)
                      (where params-clause))]
     (-> (su/find-one conn stmt)
         vals
         first))))

(defn- params->sql-params
  "Converts the search params into params that can be converted in sql condition clause."
  [provider params]
  #_(when-not (:provider-id params)
    (errors/internal-error!
      (format "Provider id is missing from find concepts parameters [%s]" params)))
  (if (:small provider)
    (dissoc params :concept-type)
    (dissoc params :concept-type :provider-id)))

(defmulti find-concepts-in-table
  "Retrieve concept maps from the given table, handling small providers separately from
  normal providers"
  (fn [db table concept-type providers params]
    (:small (first providers))))

;; Execute a query against the small providers table
(defmethod find-concepts-in-table true
  [db table concept-type providers params]
  (mapcat (fn [provider]
            (j/with-db-transaction
              [conn db]
              (let [params (params->sql-params provider params)
                    stmt (su/build (select [:*]
                                     (from table)
                                     (when-not (empty? params)
                                       (where (sh/find-params->sql-clause params)))))]
                (doall (map (fn [res]
                              (oc/db-result->concept-map concept-type conn (:provider_id res) res))
                            (su/query conn stmt))))))
          providers))

;; Execute a query against a normal (not small) provider table
(defmethod find-concepts-in-table :default
  [db table concept-type providers params]
  (let [provider (first providers)
        provider-id (:provider-id provider)]
    (j/with-db-transaction
      [conn db]
      (let [params (params->sql-params provider params)
            stmt (su/build (select [:*]
                             (from table)
                             (when-not (empty? params)
                               (where (sh/find-params->sql-clause params)))))]
        (doall (map (fn [res]
                      (oc/db-result->concept-map concept-type conn provider-id res))
                    (su/query conn stmt)))))))

(extend-protocol c/ConceptSearch
  OracleStore

(find-concepts
  [db providers params]
  {:pre [(coll? providers)]}
  (let [concept-type (:concept-type params)
        table-provider (group-by #(tables/get-table-name % concept-type) providers)]
    (mapcat (fn [[table provider-list]]
              (find-concepts-in-table db table concept-type provider-list params))
            (seq table-provider))))

  (find-concepts-in-batches
    ([db provider params batch-size]
     (c/find-concepts-in-batches db provider params batch-size 0))
    ([db provider params batch-size requested-start-index]
     (let [{:keys [concept-type provider-id]} params
           params (params->sql-params provider params)
           table (tables/get-table-name provider concept-type)]
       (letfn [(find-batch
                 [start-index]
                 (j/with-db-transaction
                   [conn db]
                   (let [conditions [`(>= :id ~start-index)
                                     `(< :id ~(+ start-index batch-size))]
                         _ (debug "Finding batch for provider" provider-id "from id >=" start-index " and id <" (+ start-index batch-size))
                         conditions (if (empty? params)
                                      conditions
                                      (cons (sh/find-params->sql-clause params) conditions))
                         stmt (su/build (select [:*]
                                                (from table)
                                                (where (cons `and conditions))))
                         batch-result (su/query db stmt)]
                     (mapv (partial oc/db-result->concept-map concept-type conn provider-id)
                           batch-result))))
               (lazy-find
                 [start-index]
                 (let [batch (find-batch start-index)]
                   (if (empty? batch)
                     ;; We couldn't find any items  between start-index and start-index + batch-size
                     ;; Look for the next greatest id and to see if there's a gap that we can restart from.
                     (do
                       (debug "Couldn't find batch so searching for more from" start-index)
                       (when-let [next-id (find-batch-starting-id db table params start-index)]
                         (debug "Found next-id of" next-id)
                         (lazy-find next-id)))
                     ;; We found a batch. Return it and the next batch lazily
                     (cons batch (lazy-seq (lazy-find (+ start-index batch-size)))))))]
         ;; If there's no minimum found so there are no concepts that match
         (when-let [start-index (find-batch-starting-id db table params)]
           (lazy-find (max requested-start-index start-index))))))))