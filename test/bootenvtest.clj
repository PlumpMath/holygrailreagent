(ns bootenvtest
  (:require  [clojure.test :refer :all]
             [environ.boot :refer [environ]]
             [environ.core :refer [env]]
             [migratus.core :as migratus]
             [system.components.postgres :as p]
             [com.stuartsierra.component :as component]
             [clojure.java.jdbc :as jdbc]))

(def test-db-spec
  {:classname  (env :driver-class)
   :subprotocol (env :subprotocol)
   :host "127.0.0.1"
   :subname (env :database-name)
   :username (env :database-user)
   :password (env :database-password)})
                    
(def config {:store                :database
             :migration-dir        "migrations/"
             :db {:classname   (env :driver-class)
                  :subprotocol "postgres" ;;(env :subprotocol)
                  :subname     (env :database-name)}})


(deftest test-env-settings
  (is (= "org.postgresql.Driver" (env :driver-class)))
  (is (= "3000" (env :http-port))))

                                        
(deftest ^:dependency postgres-test-create-table-and-insert
  (let [db (component/start
            (p/new-postgres-database test-db-spec))
        msg "It works!"]
    (jdbc/execute! db ["CREATE TEMP TABLE test (coltest varchar(20));"])
    (jdbc/insert! db :test {:coltest msg})
    (is (= msg (:coltest (first (jdbc/query db ["SELECT * from test;"])))))
    (component/stop db)))


(deftest put-person-in-and-take-them-out
  ;;; apply pending migrations
  (migratus/migrate config)
  (jdbc/with-db-connection [db-con test-db-spec]
    (jdbc/insert! db-con :quux {:name "john" :id 1})
    (is (= "john" (:name (first (jdbc/query db-con ["SELECT * from quux;"]))))))
  ;;; rollback the last migration applied
  (migratus/rollback config))

                                        ;(migratus/migrate config)
;(migratus/up config 20150701134958)
                                        ;(migratus/down config 20150701134958)
;; 
;(migratus/rollback config)
;; ;initialize the database using the 'init.sql' script
;; (migratus/init config)



;; (migratus/reset config)
;(migratus/migrate config)
;; ;; (migratus/rollback config)
;; (migratus/down config 20160918164140)
;; ;; (migratus/down config 20160918164140)
;; ;(migratus/migrate config) 
