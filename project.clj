(defproject treefy "0.1.0-SNAPSHOT"
  :description "Transform a list of file paths into an interactively explorable tree."
  :url "https://github.com/mrucci/treefy"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :main ^:skip-aot treefy.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
