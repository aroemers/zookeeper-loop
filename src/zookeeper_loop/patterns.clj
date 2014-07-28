;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns zookeeper-loop.patterns
  (:refer-clojure :exclude [swap!])
  (:require [zookeeper :as zk])
  (:import [org.apache.zookeeper
            KeeperException$SessionExpiredException
            KeeperException$ConnectionLossException
            KeeperException$BadVersionException]))


(def ^:dynamic *deserializer* nil)
(def ^:dynamic *serializer* nil)


(defn retry
  "Tries to execute the given function on the Zookeeper client as the
  first argument. When the connection to Zookeeper cannot be
  (re)established within timeout-msec while processing the function,
  an exception is thrown. Returns the value of the evaluated function."
  [client-loop timeout-msec fn & args]
  (loop [client (deref client-loop timeout-msec ::disconnected)]
    (if-not (= ::disconnected client)
      (let [result (try (apply fn client args)
                        (catch KeeperException$SessionExpiredException see ::retry)
                        (catch KeeperException$ConnectionLossException cle ::retry))]
        (if (= ::retry result)
          (recur (deref client-loop timeout-msec ::disconnected))
          result))
      (throw (ex-info "Could not connect in time to Zookeeper for retry of function."
                      {:client @(:client-atom client-loop)})))))


(defn ensure-path
  "Ensure the given path exists. When the connection to Zookeeper
  cannot be (re)established within timeout-msec while processing the
  path, an exception is thrown. Returns the path when created, or nil
  when it already exists"
  [client-loop timeout-msec path persistent?]
  (retry client-loop timeout-mesec
         (fn [client]
           (when-not (zk/exists client path)
             (zk/create-all client path :persistent? persistent?)))))


(defn swap!
  "Apply the given fn atomically to the data of the given path,
  together with the args. When the connection to Zookeeper cannot be
  (re)established within timeout-msec while processing the swap, an
  exception is thrown. Requires *deserializer* and *serializer* to be
  bound to functions. Returns the new value."
  [client-loop timeout-msec path fn & args]
  (assert (fn? *deserializer*) "swap! needs *deserializer* to be bound to a function.")
  (assert (fn? *serializer*) "swap! needs *serializer* to be bound to a function.")
  (loop []
    (let [new-data (try (retry client-loop timeout-msec
                               (fn [client]
                                 (let [data (zk/data client path)
                                       deserialized (when-let [bytes (:data data)]
                                                      (*deserializer* bytes))
                                       new-data (apply fn deserialized args)
                                       version (-> data :stat :version)]
                                   (zk/set-data client path (*serializer* new-data) version)
                                   new-data)))
                        (catch KeeperException$BadVersionException bve ::retry))]
      (if (= ::retry new-data)
        (recur)
        new-data))))
