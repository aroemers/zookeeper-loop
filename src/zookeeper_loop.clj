;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns zookeeper-loop
  (:require [zookeeper :as zk]
            [zookeeper.internal :as zi]
            [taoensso.timbre :as timbre :refer (info debug trace)]
            ;; [clojure.walk :refer (postwalk)]
            )
  (:import [org.apache.zookeeper ZooKeeper
            ;; KeeperException KeeperException$SessionExpiredException
            ;; KeeperException$ConnectionLossException
            ]
           [java.util.concurrent CountDownLatch TimeUnit]))


(defn- deref-connected
  [{:keys [client-atom] :as client-loop} ^CountDownLatch latch]
  (loop []
    (if (or (nil? latch) (= (.getCount latch) 1))
      (let [client (deref client-atom)]
        (case (zk/state client)
          :CONNECTED client
          :CONNECTING (do (trace "Thread" (Thread/currentThread)
                                 "in ClientLoop/deref while client is connecting.")
                          (locking client-loop (.wait client-loop 1000))
                          (recur))
          :ASSOCIATING (do (trace "Thread" (Thread/currentThread)
                                  "in ClientLoop/deref while client is associating.")
                           (locking client-loop (.wait client-loop 1000))
                           (recur))
          :CLOSED client
          :AUTH_FAILED client))
      (debug "Deref interrupted by timeout."))))


(defrecord ClientLoop [client-atom connect-str timeout-msec watcher]
  clojure.lang.IDeref
  (deref [this]
    (deref-connected this nil))

  clojure.lang.IBlockingDeref
  (deref [this ms val]
    (let [latch (CountDownLatch. 1)
          fut (future (deref-connected this latch))
          result (deref fut ms val)]
      (.countDown latch)
      result)))


(defn- handle-global
  [{:keys [client-atom connect-str timeout-msec watcher] :as client-loop}
   {:keys [event-type keeper-state] :as event}]
  (when (= :None event-type)
    (when @client-atom (debug "Handling global Zookeeper event" event))
    (locking client-loop
      (case keeper-state
        :SyncConnected (do (debug "Zookeeper client connected.")
                           (.notifyAll client-loop))
        :Disconnected (do (debug "Zookeeper client disconnected.")
                          (.notifyAll client-loop))
        :Expired (let [watcher (zi/make-watcher (partial handle-global client-loop))
                       old-client @client-atom]
                   (reset! client-atom (ZooKeeper. connect-str timeout-msec watcher))
                   (.notifyAll client-loop)
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


;; (defn retry?
;;   [ex start-time optm]
;;   (when (and (or (nil? (:timeout optm))
;;                  (< (System/currentTimeMillis) (+ start-time (:timeout optm))))
;;              (#{clojure.lang.ExceptionInfo} (class ex)))
;;     ::retry))
;;
;;
;; (defmacro with-zookeeper
;;   [binding & opts+forms]
;;   (let [opts (take-while (complement list?) opts+forms)
;;         forms (drop (count opts) opts+forms)
;;         optm (apply hash-map opts)
;;         opt-sym (gensym "optm-")]
;;     `(let [~opt-sym '~optm]
;;        ~@(for [form forms]
;;            (postwalk (fn [f]
;;                        (if (and (list? f) (= (second f) 'client))
;;                          `(loop [start-time# (System/currentTimeMillis)]
;;                             (let [result#
;;                                   (try (await-connection client start-time# ~opt-sym) ~f
;;                                        (catch Exception ex#
;;                                          (or (retry? ex# start-time# ~opt-sym) (throw ex#))))]
;;                               (if (= result# ::retry) (recur start-time#) result#)))
;;                          f))
;;                      form)))))
;;
;; (with-zookeeper client
;;   :on-expire continue/error
;;   :timeout 5000
;;   (let [foo 'bar]
;;     (println client 1)
;;     (println :foobar)
;;     (println client (throw (ex-info "RAAAH!" {:client client})))))
