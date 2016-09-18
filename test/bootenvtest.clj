(ns bootenvtest
  (:require  [clojure.test :refer :all]
             [environ.boot :refer [environ]]
             [environ.core :refer [env]]
             [korma.db :as db]
             [korma.core :as korma]
             [migratus.core :as migratus]
             [clojure.java.jdbc :as jdbc]
             [clojure.edn])
  (:use [korma.db]
        [korma.core]))


(defdb db (postgres {:db (env :database-name)
                     :user (env :database-user)
                     :password (env :database-user)}))

(defentity users
   (table :quux))
                    
(def config {:store                :database
             :migration-dir        "migrations/"
             :init-script          "init.sql"
             :migration-table-name "quux"
             :db {:classname   (env :driver-class)
                  :subprotocol (env :subprotocol)
                  :subname     (env :database-name)}})


(deftest test-env-settings
  (is (= "org.postgresql.Driver" (env :driver-class)))
  (is (= "3000" (env :http-port))))

(deftest db-test []
  (let [db (:postgres system)
        msg "It works!"]
    (jdbc/execute! db ["CREATE TEMP TABLE test (coltest varchar(20));"])
    (jdbc/insert! db :test {:coltest msg})
    (= msg (:coltest (first (jdbc/query db ["SELECT * from test;"]))))))

(deftest put-and-get-user
  (migratus/migrate config)
  (insert users
          (values {:name "john" :id 1}))
  ;;; the select query returns the entire row as a map in a list
  ;;; we get out the list, and then the relevant value
  (is (= "john" (:name (first (select users (where {:name "john"}))))))
  (delete users (where {:id 1}))
  (migratus/rollback config))

