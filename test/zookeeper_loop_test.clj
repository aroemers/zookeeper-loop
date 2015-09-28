;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns zookeeper-loop-test
  (:require [clojure.test :refer :all]
            [zookeeper-loop :refer :all]
            [zookeeper :as zk]
            [taoensso.timbre :as timbre :refer (info warn)])
  (:import [org.apache.curator.test TestingServer]))


;;; Setup

(def ^:dynamic *conn-str* nil)

(defn zookeeper-fixture
  [f]
  (info "Starting embedded Zookeeper...")
  (let [zk (TestingServer.)]
    (binding [*conn-str* (.getConnectString zk)]
      (info (format "Started embedded Zookeeper on %s." *conn-str*))
      (f))
    (info "Stopping embedded Zookeeper...")
    (.stop zk)
    (info "Stopped embedded Zookeeper.")))


(use-fixtures :once zookeeper-fixture)


;;; Tests

(deftest blocking-test
  (with-open [client (client-loop *conn-str*)]
    (zk/create-all @client "foo/bar")))


(deftest timeout-test
  (warn "Not sure how to test timeouts yet")
  ;; (with-open [client (client-loop *conn-str*)]
  ;;   (is (= :timeout (deref client 0 :timeout))))
  )


(deftest non-blocking-test
  (with-open [client (client-loop *conn-str*)]
    (is (not (nil? (deref client 10000 :timeout))))))
