(ns database-url-test
  (:require
   [clojure.test :refer :all]
   [environ.core :refer [env]]))

(deftest test-database-url
  (is (= "postgresql://localhost:5432/"
         (env :database-url))))

