(ns bootenvtest
  (:require  [clojure.test :refer :all]
             [environ.boot :refer [environ]]
             [environ.core :refer [env]]
             [clojure.edn]))

;(def config (clojure.edn/read-string (slurp "tryit.edn")))
;(environ :env {:http-port "3000"})

(deftest test-env-settings
  (is (= "org.postgresql.Driver" (env :driver-class)))
  (is (= "3000" (env :http-port))))
