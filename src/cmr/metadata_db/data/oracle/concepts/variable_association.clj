(ns cmr.metadata-db.data.oracle.concepts.variable-association
  "Implements multi-method variations for variables"
  (:require
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :variable-association
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :variable-association)
          (assoc-in [:extra-fields :associated-concept-id] (:associated_concept_id result))
          (assoc-in [:extra-fields :associated-revision-id]
                    (when-let [ari (:associated_revision_id result)]
                      (long ari)))
          (assoc-in [:extra-fields :variable-concept-id] (:variable_concept_id result))
          (assoc :user-id (:user_id result))))

(defn- var-assoc-concept->insert-args
  [concept]
  (let [{{:keys [associated-concept-id associated-revision-id variable-concept-id]} :extra-fields
         :keys [user-id]} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["associated_concept_id" "associated_revision_id" "variable_concept_id" "user_id"])
     (concat values [associated-concept-id associated-revision-id variable-concept-id user-id])]))

(defmethod c/concept->insert-args [:variable-association false]
  [concept _]
  (var-assoc-concept->insert-args concept))

(defmethod c/concept->insert-args [:variable-association true]
  [concept _]
  (var-assoc-concept->insert-args concept))
