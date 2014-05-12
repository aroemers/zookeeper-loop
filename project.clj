;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(defproject zookeeper-loop "0.1.0-SNAPSHOT"
  :description "An automatically reconnecting Zookeeper client."
  :url "https://github.com/Shared"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [zookeeper-clj "0.9.1"]
                 [com.taoensso/timbre "3.2.1"]]
  :profiles {;; Stupid profile needed, otherwise mvn won't include zookeeper in its compile
             ;; phase dependencies. To test, call `lein with-profile +curator test`.
             :curator {:dependencies [[org.apache.zookeeper/zookeeper "3.4.5" :exclusions
                                       [com.sun.jmx/jmxri com.sun.jdmk/jmxtools javax.jms/jms junit]]
                                      [org.apache.curator/curator-test "2.4.2"]]}}
  :pom-plugins [[com.theoryinpractise/clojure-maven-plugin "1.3.15"
                 {:extensions "true"
                  :executions ([:execution
                                [:id "clojure-compile"]
                                [:phase "compile"]
                                [:configuration
                                 [:temporaryOutputDirectory "true"]
                                 [:sourceDirectories [:sourceDirectory "src"]]]
                                [:goals [:goal "compile"]]])}]])
