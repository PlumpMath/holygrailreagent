(ns holy-grail.systems
  (:require 
   [holy-grail.handler :refer [app]]
   [environ.core :refer [env]]
   [clojure.java.jdbc :as jdbc]
   [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
   [system.core :refer [defsystem]]
   [holy-grail.handler :refer [event-msg-handler*]]
   (system.components 
    [sente :refer [new-channel-sockets]]
    [http-kit :refer [new-web-server]]
    [postgres :refer [new-postgres-database]]
    [repl-server :refer [new-repl-server]])))

 
(def postgres-spec
  {:classname   (str (env :driver-class))
   :subprotocol "postgresql"
   :host "127.0.0.1"
   :subname (env :database-name)
   :username (env :database-user)
   :password (env :database-password)})


(defsystem dev-system
  [:web (new-web-server (Integer. (env :http-port)) app)
   ;;; This is brilliant. This replaces our explicit call to start the channel socket up.
   ;;; So much more elegant.
   :sente (new-channel-sockets event-msg-handler* sente-web-server-adapter)
   :postgres (new-postgres-database postgres-spec)])
   

(defsystem prod-system
  [:web (new-web-server (Integer. (env :http-port)) app)
   :repl-server (new-repl-server (Integer. (env :repl-port)))])
