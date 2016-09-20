(ns holy-grail.database
  (:require
   [clojure.java.jdbc :as jdbc]
   [oj.core :as oj :rename [update oj/update]]
   [system.repl :refer [system]]))

(defn create-user []
  (let [db (:postgres system)]
    (jdbc/insert! db :quux {:name "john" :id 1 :password "secret!"})))

(defn retrieve-user-name-and-password []
  (let [db (:postgres system)]
     (oj/exec {:table :quux
               :select [:name :password]
               :where {:name "john"}} db)))

(defn db-test []
  (let [;; user (env :database-user)
        ;; pwd (env :database-password)
        ;; name (env :database-name)
        db (:postgres system)
        msg "It works!"]
    (jdbc/execute! db ["CREATE TEMP TABLE test (coltest varchar(20));"])
    (jdbc/insert! db :test {:coltest msg})
    (= msg (:coltest (first (jdbc/query db ["SELECT * from test;"]))))))
