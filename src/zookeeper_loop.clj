;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns zookeeper-loop
  (:require [zookeeper :as zk]
            [zookeeper.internal :as zi]
            [taoensso.timbre :as timbre :refer (info debug trace)])
  (:import [org.apache.zookeeper ZooKeeper]
           [java.util.concurrent CountDownLatch TimeUnit]))


(defn- deref-connected
  [{:keys [client-atom] :as client-loop} ^CountDownLatch latch]
  (loop [first? true]
    (if (or (nil? latch) (> (.getCount latch) 0))
      (let [client (deref client-atom)
            state (zk/state client)]
        (case state
          :CONNECTED client
          (:CONNECTING :ASSOCIATING) (do (trace "Thread" (Thread/currentThread)
                                                "in ClientLoop/deref while client is" state)
                                         (locking client-loop
                                           (.wait ^Object client-loop 1000))
                                         (when (and first? latch)
                                           (.countDown latch))
                                         (recur false))
          :CLOSED client
          :AUTH_FAILED client))
      (debug "Deref interrupted by timeout."))))


(defrecord ClientLoop [client-atom connect-str timeout-msec watcher]
  clojure.lang.IDeref
  (deref [this]
    (deref-connected this nil))

  clojure.lang.IBlockingDeref
  (deref [this ms val]
    (let [latch (CountDownLatch. 2)]
      (future (locking this (.wait ^Object this ms))
              (.countDown latch))
      (let [result (deref-connected this latch)]
        (locking this (.notifyAll ^Object this))
        (or result val)))))


(defn- handle-global
  [{:keys [client-atom connect-str timeout-msec watcher] :as client-loop}
   {:keys [event-type keeper-state] :as event}]
  (when (= :None event-type)
    (when @client-atom (debug "Handling global Zookeeper event" event))
    (locking client-loop
      (case keeper-state
        :SyncConnected (do (debug "Zookeeper client connected.")
                           (.notifyAll ^Object client-loop))
        :Disconnected (do (debug "Zookeeper client disconnected.")
                          (.notifyAll ^Object client-loop))
        :Expired (let [watcher (zi/make-watcher (partial handle-global client-loop))
                       old-client @client-atom]
                   (reset! client-atom (ZooKeeper. connect-str timeout-msec watcher))
                   (.notifyAll ^Object client-loop)
                   (when old-client
                     (debug "Zookeeper client expired, created new one, closing old one.")
                     (zk/close old-client))))))
  (when watcher (watcher event)))


(defn client-loop
  [connect-str & {:keys [timeout-msec watcher client-atom]
                  :or {timeout-msec 5000
                       client-atom (atom nil)}}]
  (info "Starting Zookeeper client loop.")
  (let [client-loop (ClientLoop. client-atom connect-str timeout-msec watcher)]
    (handle-global client-loop {:event-type :None :keeper-state :Expired})
    client-loop))


(defn close-loop
  [{:keys [client-atom] :as client-loop}]
  (info "Closing Zookeeper client and stopping loop.")
  (zk/close @client-atom))
