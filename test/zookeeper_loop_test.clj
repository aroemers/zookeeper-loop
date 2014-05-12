;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns zookeeper-loop-test
  (:require [clojure.test :refer :all]
            [zookeeper-loop :refer :all]
            [zookeeper :as zk]
            [taoensso.timbre :as timbre :refer (info)])
  (:import [org.apache.curator.test TestingServer]))


(defn zookeeper-fixture
  [f]
  (info "Starting embedded Zookeeper...")
  (let [zk (TestingServer. 12181)]
    (info "Started embedded Zookeeper.")
    (f)
    (info "Stopping embedded Zookeeper...")
    (.stop zk)
    (info "Stopped embedded Zookeeper.")))


(use-fixtures :once zookeeper-fixture)


(deftest simple-test
  (let [client (client-loop "localhost:12181")]
    (zk/create-all @client "foo/bar")
    (close-loop client)))
