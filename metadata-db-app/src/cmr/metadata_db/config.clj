(ns cmr.metadata-db.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.oracle.config :as oracle-config]
            [cmr.oracle.connection :as conn]
            [cmr.message-queue.config :as rmq-conf]))

(defconfig metadata-db-username
  "The database username"
  {:default "METADATA_DB"})

(defconfig metadata-db-password
  "The database password"
  {:default "METADATA_DB"})

(defconfig catalog-rest-db-username
  "The catalog rest db username"
  {:default "DEV_52_CATALOG_REST"})

(defn db-spec
  "Returns a db spec populated with config information that can be used to connect to oracle"
  [connection-pool-name]
  (conn/db-spec
    connection-pool-name
    (oracle-config/db-url)
    (oracle-config/db-fcf-enabled)
    (oracle-config/db-ons-config)
    (metadata-db-username)
    (metadata-db-password)))

(defconfig parallel-chunk-size
  "Gets the number of concepts that should be processed in each thread of get-concepts."
  {:default 200
   :type Long})

(defconfig result-set-fetch-size
  "Gets the setting for query fetch-size (number of rows to fetch at once)"
  {:default 200
   :type Long})

(defconfig metadata-db-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig ingest-exchange-name
  "The ingest exchange to which concept update/save messages are published."
  {:default "cmr_ingest.exchange"})

(defconfig access-control-exchange-name
  "The access control exchange to which update/save messages are published for access control data."
  {:default "cmr_access_control.exchange"})

(def concept-type->exchange-name-fn
  "Maps concept types to a function that returns the name of the exchange to publish the message to."
  {:granule ingest-exchange-name
   :collection ingest-exchange-name
   :tag ingest-exchange-name
   :tag-association ingest-exchange-name
   ;; Add service when we add service indexing
   :service nil
   :access-group access-control-exchange-name
   :acl access-control-exchange-name})

(defconfig deleted-collection-revision-exchange-name
  "An exchange that will have messages passed to it whenever a collection revision is removed
  from metadata db."
  {:default "cmr_deleted_collection_revision.exchange"})

(defconfig publish-messages
  "This indicates whether or not messages be published to the exchange"
  {:default true :type Boolean})

(defn rabbit-mq-config
  "Returns the rabbit mq configuration for the metadata db application."
  []
  (assoc (rmq-conf/default-config)
         :exchanges [(deleted-collection-revision-exchange-name)
                     (ingest-exchange-name)
                     (access-control-exchange-name)]))
