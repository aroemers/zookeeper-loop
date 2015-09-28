;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(defproject functionalbytes/zookeeper-loop "0.1.0"
  :description "An automatically reconnecting Zookeeper client."
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [zookeeper-clj "0.9.1"]
                 [com.taoensso/timbre "3.2.1"]
                 [org.apache.curator/curator-test "2.9.0" :scope "test"]
                 [org.apache.zookeeper/zookeeper "3.4.6"]]
  :pom-plugins [[com.theoryinpractise/clojure-maven-plugin "1.7.1"
                 {:extensions "true"
                  :executions ([:execution
                                [:id "clojure-compile"]
                                [:phase "compile"]
                                [:configuration
                                 [:temporaryOutputDirectory "true"]
                                 [:sourceDirectories [:sourceDirectory "src"]]]
                                [:goals [:goal "compile"]]]
                               [:execution
                                [:id "clojure-test"]
                                [:phase "test"]
                                [:configuration
                                 [:temporaryOutputDirectory "true"]
                                 [:sourceDirectories [:sourceDirectory "test"]]]
                                [:goals [:goal "test"]]])}]]
  :global-vars {*warn-on-reflection* true})
