(set-env!
 :source-paths   #{"src/cljs" "src/clj"}
 :resource-paths #{"resources"}
 :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                 [adzerk/boot-reload "0.4.12" :scope "test"]

                 [adzerk/boot-cljs-repl   "0.3.0" :scope "test"]
                 [com.cemerick/piggieback "0.2.1"  :scope "test"]
                 [weasel                  "0.7.0"  :scope "test"]

                 [devcards                "0.2.1-7"   :scope "test" :exclusions [cljsjs/react cljsjs/react-dom]]

                 [org.clojure/clojure "1.8.0"]

                 [environ "1.0.3"]
                 [boot-environ "1.0.3"]

                 [org.danielsz/system "0.3.0-SNAPSHOT"]
                 [org.clojure/tools.nrepl "0.2.12"]

                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.4.0"]
                 [pandeiro/boot-http            "0.7.3"     :scope "test"]
                 [crisptrutski/boot-cljs-test   "0.2.1"     :scope "test"]
                 ; client
                 [reagent                       "0.6.0-rc"]
                 [org.omcljs/om "0.9.0"]
                 [org.clojure/clojurescript     "1.9.216"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[holy-grail.systems :refer [dev-system prod-system]]
 '[pandeiro.boot-http            :refer [serve]]
 '[crisptrutski.boot-cljs-test   :refer [test-cljs]]
 '[environ.boot :refer [environ]]
 '[system.boot :refer [system run]])


(deftask dev
  "Run a restartable system in the Repl"
  []
  (comp
   (environ :env {:http-port "3000"})
   (watch :verbose true)
   (system :sys #'dev-system :auto true :files ["handler.clj"])
   (reload)
 
   (cljs :source-map true
         :optimizations :none)
   (sift :include #{#"\.cljs\.edn$"} :invert true)
   (repl :server true)
   (speak)))


(deftask dev-cljs-repl
  "Run a restartable system in the Repl"
  []
  (comp
   (environ :env {:http-port "3000"})
   (watch :verbose true)
   (system :sys #'dev-system :auto true :files ["handler.clj"])
   (reload)
   (cljs-repl)
   (cljs :source-map true)))

(deftask dev-run
  "Run a dev system from the command line"
  []
  (comp
   (environ :env {:http-port "3000"})
   (cljs)
   (run :main-namespace "holy-grail.core" :arguments [#'dev-system])
   (wait)))

(deftask prod-run
  "Run a prod system from the command line"
  []
  (comp
   (environ :env {:http-port "8008"
                  :repl-port "8009"})
   (cljs :optimizations :advanced)
   (run :main-namespace "holy-grail.core" :arguments [#'prod-system])
   (wait)))


