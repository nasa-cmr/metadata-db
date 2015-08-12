(ns migrations.018-add-user-id-collection-tables
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 18."
  []
  (println "migrations.018-add-user-id-collection-tables up...")
  (h/sql "alter table small_prov_collections add user_id VARCHAR(50)")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s add user_id VARCHAR(30)" t))))

(defn down
  "Migrates the database down from version 18."
  []
  (println "migrations.018-add-user-id-collection-tables down...")
  (h/sql "alter table small_prov_collections drop column user_id")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s drop column user_id" t))))
