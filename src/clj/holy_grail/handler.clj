(ns holy-grail.handler
  (:require
   [holy-grail.api                 :refer :all]
   [holy-grail.services            :refer [swag]]
   [holy-grail.database            :refer :all]
   [system.repl                    :refer [system]]
   [clj-time.core                  :as time]

   [liberator.core                 :refer [defresource]]
   [liberator.representation       :refer [ring-response]]
   [buddy.sign.jwt                 :as jwt]
   [buddy.sign.jws                 :as jws]
   [buddy.sign.jwe                 :as jwe]
   [buddy.auth                     :refer [authenticated? throw-unauthorized]]
   [buddy.auth.backends.token      :refer [jwe-backend]]
   [buddy.auth.middleware          :refer [wrap-authentication wrap-authorization]]
   [buddy.hashers                  :as hashers]
   [buddy.core.nonce               :as nonce]   
   [taoensso.sente                 :as sente]
   [taoensso.timbre                :as timbre :refer (tracef debugf infof warnf errorf)]

   [clojure.java.io                :as io]
   [compojure.core                 :refer [defroutes GET POST ANY wrap-routes routes]]
   [compojure.route                :as route]
   [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
   [ring.middleware.defaults       :refer [wrap-defaults site-defaults]]
   [ring.middleware.params         :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.util.response             :refer [response content-type resource-response]]))


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
  

(defresource mustbeloggedin [request]
  :authorized? (fn [request] (authenticated? request))
  :available-media-types ["text/plain" "application/clojure;q=0.9"]
  :allowed-methods [:get :post]
  :handle-ok (fn [] {:yourock "no, really"}))

(defresource check-ring-map [ctx]
  :available-media-types ["text/plain" "application/clojure"]
  :handle-ok (fn [ctx] (str ctx)))

(def my-secret (nonce/random-bytes 32))

(defresource sign-up-handler [ring-req]
  :available-media-types ["text/plain" "application/clojure"]
  :allowed-methods [:get :post]
  :handle-created (fn [ring-req]
                    (let [{:keys [request session params]} ring-req
                          {:keys [user-id name password]} params]
                      (println (str "Hey look here " (:identity (assoc ring-req :identity "hello"))))
                      (if (and (= name "Timothy") (= password "mysecret"))
                        (let [claims {:user (keyword name)
                                      :exp (time/plus (time/now) (time/seconds 3600))}
                              token (jwt/encrypt claims my-secret {:alg :a256kw :enc :a128gcm})]
                          
                          (ring-response {:status 200 :session (assoc session :identity token :uid user-id :token token)}))))))
                                         

(defn ok [d] {:status 200 :body d})
(defn bad-request [d] {:status 400 :body d})

(defn home
  [request]
  (if-not (authenticated? request)
    (throw-unauthorized)
    (ok {:status "Logged" :message (str "hello logged user "
                                        (:identity request))})))

(defn login
  [request]
  (let [{:keys [request session params]} request
        {:keys [user-id name password]} params
        valid? (and (= name "Timothy") (= password "mysecret"))]
    (println (str valid?))
    (if valid?
      (let [claims {:user (keyword name)
                    :exp (time/plus (time/now) (time/seconds 3600))}
            token (jwt/encrypt claims my-secret {:alg :a256kw :enc :a128gcm})
            updated-session (-> session
                                  (assoc :identity (:username token)))
            ]
        (do
          (println (str session))
          (assoc session :session updated-session)
        
          (ok {:token token})))
      (bad-request {:message "wrong auth data"}))))

(defroutes home-routes
  (GET "/" [] (-> (resource-response "index.html")
                  (content-type "text/html")))
  (GET "/docs" [] (-> (resource-response "docs/docs.md")
                      (content-type "text/plain; charset=utf-8")))
  (GET "/home" [] home)
  (POST "/login" [] login)
  (ANY "/checkringmap"      ring-req (check-ring-map ring-req))
  (ANY "/signup"            ring-req (sign-up-handler ring-req))
  (ANY "/mustbeloggedin"    ring-req (mustbeloggedin  ring-req))
    ;;; Add these 2 entries: --->
  ;; (GET  "/chsk"  req ((:ring-ajax-get-or-ws-handshake (:sente system)) req))
  ;; (POST "/chsk"  req ((:ring-ajax-post (:sente system)) req))  
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
  (send-fn :sente/all-users-without-uid [:me/logger {:message (str (mustbeloggedin req))}]))


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

(def auth-backend (jwe-backend {:secret my-secret :options {:alg :a256kw :enc :a128gcm}}))

(def middleware (-> site-defaults
                 (assoc-in [:static :resources] "/")))
                 

(def app
  (as-> myroutes $
;      (wrap-authorization $ auth-backend)
      (wrap-authentication $ auth-backend)
      (wrap-defaults $ middleware)))
