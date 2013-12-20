(defproject io.pedestal/cornice "0.1.0-SNAPSHOT"
  :description "The Pedestal Cornice library"
  :url "http://pedestal.io"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/clojurescript "0.0-2127"]]
  :plugins [[com.keminglabs/cljx "0.3.1"]
            [lein-cljsbuild "1.0.1"]]
  :hooks [leiningen.cljsbuild cljx.hooks]
  :source-paths ["target/generated/clj"]
  :cljsbuild {:builds [{:source-paths ["target/generated/cljs"]
                        :jar true
                        :compiler {:output-to "target/cornice.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]}
  :cljx {:builds [{:source-paths ["src/"]
                   :output-path "target/generated/clj"
                   :rules :clj}
                  {:source-paths ["src/"]
                   :output-path "target/generated/cljs"
                   :rules :cljs}]})
