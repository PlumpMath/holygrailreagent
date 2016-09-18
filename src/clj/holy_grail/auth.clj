(ns holy-grail.auth
  (:require [environ.core               :refer [env]]
            [system.repl :refer [system]]
            [taoensso.sente :as sente]
            [buddy.sign.jws             :as jws]
            [buddy.sign.jwe :as jwe]
            [clojure.java.jdbc :as jdbc]
            [korma.core]
            [clj-time.core              :as time]
            [buddy.auth.backends.token  :refer [jws-backend]]
            [buddy.hashers              :as hashers]))

;; (defn create-test-table
;;   [table db]
;;   (jdbc/db-do-commands db
;;     (/create-table table
;;        [[:username "VARCHAR(32)" "PRIMARY KEY"]
;;         [:password "VARCHAR(100)"]]
;;        {:table-spec ""})))

;; (defn insert-user [ds user]
;;   (let [db (:postgres system)]
;;     (create-test-table :users db)
;;     (jdbc/insert! db :users {:username (:username user) :password (:password user)})))

;; (defn insert-user [ds user]
;;   (let [db (:postgres system)]
;;     (jdbc/execute! db ["CREATE TABLE test (coltest varchar(20));"])
;;     (jdbc/insert! db :user {:username (:username user) :password (:password user)})))

(defn get-user [username]
  (let [db (:postgres system)]
    (jdbc/execute! db ["SELECT * WHERE USERNAME =" username])))

(defn encrypt-password
  [password]
  (hashers/encrypt password {:alg :pbkdf2+sha256}))

(defn check-password
  [encrypted unencrypted] 
  (hashers/check unencrypted encrypted))


(defn make-token-pair!
  [user]
  {:token {:auth-token (jwe/encrypt {:user user} {:alg :rsa-oaep :enc :a128cbc-hs256})
           :refresh-token nil}}) 
           ;;https://rundis.github.io/blog/2015/buddy_auth_part3.html
           ;;use this in the future to store refresh a refresh token.
           

;; (defmulti handle-event
;;   "Handle events based on the event ID."
;;   (fn [[ev-id ev-arg] ring-req] ev-id))

;; (defmethod handle-event :session/register
;;   [[_ [username password email]] req]
;;   (when-let [uid (get-in req [:session :uid])]
;;     (let [params {:email email
;;                   :password password
;;                   :username username}
;;           token (make-token-pair! username)]
;;         (do (insert-user {:email email
;;                               :password (encrypt-password password)
;;                               :username username})
;;             (chsk-send! uid [:register/success token]))
;;         ;(chsk-send! uid [:register/fail error-msgs])
;;         )))


;; ;; Reply with authentication failure or success. For a successful authentication, remember the login.
;; (defmethod handle-event :session/auth
;;   [[_ [username password]] req]
;;   (when-let [uid (get-in req [:session :uid])]
;;     (let [db-entry (get-user username)
;;           db-hashed-password (:password db-entry)
;;           user-exists? (fn [user] db-entry) 
;;           password-match? (fn [pass] (check-password db-hashed-password pass))
;;           token (make-token-pair! username)]
;;         (chsk-send! uid [:auth/success token])
;;         (chsk-send! uid [:auth/fail]))))

