(defproject truck-cache "0.1.0-SNAPSHOT"
  :description "Front cache"
  :url "https://github.com/little-arhat/truck-cache"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [ring "1.4.0"]
                 [http-kit "2.1.19"]
                 [compojure "1.4.0"]
                 [environ "1.0.2"]]
  :plugins [[lein-environ "1.0.2"]]
  :main ^:skip-aot truck-cache.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
