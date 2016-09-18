(ns holy-grail.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [cljs.spec :as s :include-macros true]
   [clojure.test.check.generators]
   [clojure.string :as str]
   [holy-grail.config :as config]
   [holy-grail.events]
   [holy-grail.subs]
;   [ajax.core :refer [GET POST]]
;   [markdown.core :refer [md->html]]
   [secretary.core :as secretary :refer-macros [defroute]]
   [goog.history.EventType :as HistoryEventType]
   [cljs.core.async :as async :refer (<! >! put! chan)]
   [taoensso.sente  :as sente :refer (cb-success?)] ; <--- Add this
   [taoensso.encore :as enc    :refer (tracef debugf infof warnf errorf format)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [taoensso.encore :as encore :refer-macros (have have?)]
   [goog.events :as events]
   [devtools.core :as devtools]
   [re-frame.core :refer [subscribe dispatch dispatch-sync]])
  (:import [goog History]
           [goog.history EventType])
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

;;; Add this: --->
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto})] ; e/o #{:auto :ajax :ws}
       
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state))   ; Watchable, read-only atom

; Dispatch on event key which is 1st elem in vector
(defmulti push-msg-handler (fn [[id _]] id)) 

(defmethod push-msg-handler :rente/testevent
  [[_ event]]
  (js/console.log "Received :rente/testevent from server: %s " (pr-str event)))

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:

(defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event]}]
    (js/console.log "Unhandled event: %s" (pr-str event)))

(defmethod event-msg-handler :chsk/state
  [old-ev-msg new-ev-msg]
  (if (= (:?data new-ev-msg) {:first-open? true})
    (js/console.log "Channel socket successfully established!")
    (js/console.log "Channel socket state change: %s" (pr-str new-ev-msg))))

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (push-msg-handler ?data))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (debugf "Handshake: %s" ?data)))
      
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))

(defn test-socket-callback []
  (chsk-send!
    [:rente/testevent {:message "Hello socket Callback!"}]
    2000
    #(js/console.log "CALLBACK from server: " (pr-str %))))

(defn test-socket-event []
  (chsk-send! [:rente/testevent {:message "Hello socket Event!"}]))

(defn test-login []
  (chsk-send! [:me/logger {:params 123}]))

;;; With this simple method, I'm able to explicitly send a CSRF token.
;;; This was sent to us when we started up our websocket connection.
;;; NO MORE ROUNDABOUT PROCEDURES
(defn login [user-id]
  (sente/ajax-lite "/login"
                   {:method :post
                    :headers {:X-CSRF-Token (:csrf-token @chsk-state)}
                    :params  {:user-id (str user-id)}}
                   #(js/console.log "CALLBACK from server: " (pr-str %))))
                   ;; (fn [response]
                   ;;   (js/console.log (js->clj :keywordize-keys true response)))))
;; -- Routes and History ------------------------------------------------------

(defroute "/" [] (dispatch [:set-showing :all]))
(defroute "/:filter" [filter] (dispatch [:set-showing (keyword filter)]))


;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))


;; -- Entry Point -------------------------------------------------------------


(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val (reagent/atom title)
        stop #(do (reset! val "")
                  (when on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
               (when (seq v) (on-save v))
               (stop))]
    (fn [props]
      [:input (merge props
                     {:type "text"
                      :value @val
                      :auto-focus true
                      :on-blur save
                      :on-change #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                     13 (save)
                                     27 (stop)
                                     nil)})])))

(defn todo-item
  []
  (let [editing (reagent/atom false)]
    (fn [{:keys [id done title]}]
      [:li {:class (str (when done "completed ")
                        (when @editing "editing"))}
        [:div.view
          [:input.toggle
            {:type "checkbox"
             :checked done
             :on-change #(dispatch [:toggle-done id])}]
          [:label
            {:on-double-click #(reset! editing true)}
            title]
          [:button.destroy
            {:on-click #(dispatch [:delete-todo id])}]]
        (when @editing
          [todo-input
            {:class "edit"
             :title title
             :on-save #(dispatch [:save id %])
             :on-stop #(reset! editing false)}])])))


(defn task-list
  []
  (let [visible-todos (subscribe [:visible-todos])
        all-complete? (subscribe [:all-complete?])]
    (fn []
      [:section#main
        [:input#toggle-all
          {:type "checkbox"
           :checked @all-complete?
           :on-change #(dispatch [:complete-all-toggle (not @all-complete?)])}]
        [:label
          {:for "toggle-all"}
          "Mark all as complete"]
        [:ul#todo-list
          (for [todo  @visible-todos]
            ^{:key (:id todo)} [todo-item todo])]])))


(defn footer-controls
  []
  (let [footer-stats (subscribe [:footer-counts])
        showing       (subscribe [:showing])]
    (fn []
      (let [[active done] @footer-stats
            a-fn (fn [filter-kw txt]
                     [:a {:class (when (= filter-kw @showing) "selected")
                          :href (str "#/" (name filter-kw))} txt])]
        [:footer#footer

          [:span#todo-count
            [:strong active] " " (case active 1 "item" "items") " left"]
         [:ul#filters
          [:li (a-fn :all    "All")]
          [:li (a-fn :active "Active")]
          [:li (a-fn :done   "Completed")]]
         (when (pos? done)
           [:button#clear-completed {:on-click #(dispatch [:clear-completed])}
             "Clear completed"])]))))


(defn task-entry
  []
  (let [title (subscribe [:header])]
   [:header#header
     [:h1 @title]
     [todo-input
       {:id "new-todo"
        :placeholder "What needs to be done?"
        :on-save #(dispatch [:add-todo %])}]]))

(defn undo-button
  []
  (let [undos?  (subscribe [:undos?])]      ;; only enable the button when there's undos
    (fn []
      [:button {:on-click #(dispatch [:undo])
                :disabled (not @undos?)}
       "Click Here to Undo Clear-All"])))

(defn socket-button []
  (fn []
    [:button {:on-click test-socket-event}
       "Go WebSocket!"]))

(defn login-button []
  (fn []
    [:button {:on-click #(login 123)}
       "LOG IN!"]))

(defn other-login-button []
  (fn []
    [:button {:on-click test-login}
       "Trying it out!"]))


(defn todo-app
  []
  (let [todos  (subscribe [:todos])]
    (fn []
      [:div
       [:section#todoapp
        [task-entry]
        (when (seq @todos)
          [task-list])
        [footer-controls]]
         
       [:footer#info
        [:p "Double-click to edit a todo"]
        [undo-button]
        [:br]
        [socket-button]
        [:br]
        [:br]
        [login-button]
        [:br]
        [:br]
        [other-login-button]
        ]
         ])))


;; -------------------------
;; Initialize app
;; (defn fetch-docs! []
;;   (GET "/docs" {:handler #(dispatch [:set-docs %])}))

(defn render
  "Render the application."
  []
  (reagent/render [todo-app] js/container)
   ;;; Start the Sente router.
   ;;; Sends crazy messages if put in our app call.
  ;;; I guess the page has to be rendered already.
  (sente/start-chsk-router! ch-chsk event-msg-handler*))


  

(defn app
  "Configure and bootstrap the application."
  []
  (dispatch-sync [:initialise-db])
  (when (identical? config/production false)
    ;; -- Debugging aids ----------------------------------------------------------
    (devtools/install!)       ;; we love https://github.com/binaryage/cljs-devtools
    (enable-console-print!))   ;; so println writes to console.log
  (hook-browser-navigation!)

;  (fetch-docs!)
  (render))
