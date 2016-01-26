(ns migrations.028-create-global-transaction-sequence
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 28."
  []
  (println "migrations.028-create-global-transaction-sequence up...")
  (when-not (h/sequence-exists? "global_transaction_id_seq")
    (let [table-name (h/get-all-concept-tablenames)
          max-rev-id (reduce (fn [acc t]
                              (let [resp (h/query (format "select max(revision_id) as max_rev_id from %s" t))
                                    value (-> resp first :max_rev_id)]
                                (if value
                                  (max acc (long value))
                                  acc)))
                      0
                      (h/get-all-concept-tablenames))]
     (h/sql
       (format "CREATE SEQUENCE METADATA_DB.global_transaction_id_seq START WITH %s INCREMENT BY 1 CACHE 400"
               (+ 100000 max-rev-id))))))

(defn down
  "Migrates the database down from version 28."
  []
  (println "migrations.028-create-global-transaction-sequence down...")
  (h/sql "DROP SEQUENCE GLOBAL_TRANSACTION_ID_SEQ"))