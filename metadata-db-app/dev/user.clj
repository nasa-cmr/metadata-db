(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.message-queue.queue.rabbit-mq :as rmq]
            [cmr.metadata-db.system :as system]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.metadata-db.data.memory-db :as memory]
            [cmr.common.dev.util :as d]
            [cmr.mock-echo.system :as mock-echo])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]
        [cmr.common.dev.capture-reveal]))

(def system nil)
(def mock-echo nil)
(def side-api-server nil)

(def use-external-db?
  "Set to true to use the Oracle DB"
  true)
  ; false)

(def use-external-mq?
  "Set to true to use Rabbit MQ"
  ; true
  false)

(defn start
  "Starts the current development system."
  []

  ;; Start side api server so we can eval things in the dev system jvm
  (alter-var-root
   #'side-api-server
   (constantly (-> (side-api/create-side-server
                     ;; We need no extra routes beyond what the side server provides.
                     (fn [_]))
                   (l/start nil))))

  (alter-var-root #'mock-echo
                  (constantly
                    (mock-echo/start (mock-echo/create-system))))

  (let [s (-> (system/create-system)
              (update-in [:db] #(if use-external-db?
                                  %
                                  (memory/create-db)))

              (update-in [:queue-broker] #(when use-external-mq? %))

              ((fn [sys] (if use-external-db?
                          sys
                          (dissoc sys :scheduler)))))]

    (alter-var-root #'system (constantly (system/start s))))

  (d/touch-user-clj)
  (d/touch-files-in-dir "src/cmr/metadata_db/services"))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'mock-echo
                  (fn [s] (when s (mock-echo/stop s))))
  (alter-var-root #'system
                  (fn [s] (when s (system/stop s)))))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(info "Custom user.clj loaded.")
