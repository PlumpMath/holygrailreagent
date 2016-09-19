(ns holy-grail.handler
  (:require
   [holy-grail.api :refer :all]
   [holy-grail.services :refer [swag]]
   [system.repl :refer [system]]
   [buddy.sign.jws             :as jws]
   [buddy.sign.jwe :as jwe]
   [clj-time.core              :as time]
   [buddy.auth.backends.token  :refer [jws-backend]]
   [buddy.hashers              :as hashers]
   [taoensso.sente :as sente]
   [taoensso.sente.server-adapters.http-kit      :refer (get-sch-adapter)]
   [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]

   ;;; These should be here temporarily.
   [environ.core :refer [env]]
   [migratus.core :as migratus]
   [clojure.java.io :as io]
   [clojure.java.jdbc :as jdbc]
   [compojure.core :refer [defroutes GET POST wrap-routes routes]]
   [compojure.route :as route]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.util.response :refer [response content-type resource-response]]))

(def config {:store                :database
             :migration-dir        "migrations/"
             :init-script          "init.edn"
             :migration-table-name "quux"
             :db {:classname   "org.postgresql.Driver"
                  :subprotocol "postgres" ;(env :subprotocol)
                  :subname    "learning_db"}})

(migratus/down config 20150701134958)
(defn db-test []
  (let [db (:postgres system)
        msg "It works!"]
    (jdbc/execute! db ["CREATE TEMP TABLE test (coltest varchar(20));"])
    (jdbc/insert! db :test {:coltest msg})
    (= msg (:coltest (first (jdbc/query db ["SELECT * from test;"]))))))


;;; Add this: --->
;;;
(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (:sente system)]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids)) ; Watchable, read-only atom
  

(defn login-handler
  "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
  In our simplified example we'll just always successfully authenticate the user
  with whatever user-id they provided in the auth request."
  [ring-req]
        ;;;; Get the values from :session and :params from the ring request.
  (let [{:keys [session params]} ring-req
        ;;;; Get the value for :user-id from the param
        {:keys [user-id]} params]
    ;;; I don't know what debugf does
    (debugf "Login request: %s" params)
    ;;; To authenticate the user, we just add :uid and their correct id to the session.
    ;;; 
    {:status 200 :session (assoc session :uid user-id)}))

(defroutes home-routes
  (GET "/" [] (-> (resource-response "index.html")
                  (content-type "text/html")))
  (GET "/docs" [] (-> (resource-response "docs/docs.md")
                      (content-type "text/plain; charset=utf-8")))

  (POST "/login" ring-req (login-handler                 ring-req))
  (GET "/db" [] (str (db-test)))

    ;;; Add these 2 entries: --->
  (GET  "/chsk"  req ((:ring-ajax-get-or-ws-handshake (:sente system)) req))
  (POST "/chsk"  req ((:ring-ajax-post (:sente system)) req))  
  (GET "/devcards" [] (-> (resource-response "devcards.html")
                          (content-type "text/html"))))


(defmulti event-msg-handler :id) ; Dispatch on event-id

(defmethod event-msg-handler :rente/testevent
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (if ?reply-fn
    (?reply-fn [:rente/testevent {:message (str "Hello socket from server Callback, received: " ?data)}])
    (send-fn :sente/all-users-without-uid [:rente/testevent {:message (str "Hello socket from server Event (no callback), received: " ?data)}])))

(defmethod event-msg-handler :me/logger
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}
   req req]
  (send-fn :sente/all-users-without-uid [:me/logger (login-handler req)]))


(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (println "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))



(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (debugf "Event: %s" event)
  (event-msg-handler ev-msg))

(defroutes myroutes
  (routes 
   #'home-routes
   #'swag
   (route/not-found "Not Found")))


(def middleware (-> site-defaults
                 (assoc-in [:static :resources] "/")))
                 

(def app
  (-> myroutes
      (wrap-defaults middleware)))

