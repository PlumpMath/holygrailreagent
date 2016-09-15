;; (ns holy-grail.core
;;   (:require [om.core :as om :include-macros true]
;;             [om.dom :as dom :include-macros true]))

;; (def app-state (atom {}))

;; (defn form
;;   "Om component for new paste-link"
;;   [data owner]
;;   (reify
;;     om/IDisplayName
;;     (display-name [this]
;;       "form")
;;     om/IRender
;;     (render [_]
;;       (dom/form #js {:className "input-form" :method "POST" :action "/action"}
;;                 (dom/label #js {:htmlFor "answer"} "Questions:")
;;                 (dom/input #js {:type "text" :name "answer" :id "answer"})
;;                 (dom/input #js {:type "submit" :value "Submit"})))))

;; (defn app [data owner]
;;   (reify
;;     om/IDisplayName
;;     (display-name [this]
;;       "app")
;;     om/IRender
;;     (render [this]
;;       (om/build form data))))

;; (om/root app app-state
;;          {:target (.getElementById js/document "container")})


(ns holy-grail.core
  (:require
            [holy-grail.config :as config]
            [holy-grail.reload :as reload]
            [reagent.core :as reagent]))


(def container-style
  "Style attributes applied to the CLJS application container"
  {:position "fixed"
   :top 0
   :bottom 0
   :left 0
   :right 0
   :overflow "auto"
   :background-color "white"})

(defn component []
  "Application page component."
  [:div (js/alert "ag") [:b "Hello world"]])

(defn render
  "Render the application."
  []
;  (set! js/container.style.cssText nil)
  ;; (doseq [[key val] container-style]
  ;;   (aset js/container.style (clj->js key) (clj->js val)))
  (reagent/render [component] (.getElementByID js/document "container")))

(defn app
  "Configure and bootstrap the application."
  []
  (when (identical? config/production false)
    (enable-console-print!)
    (reload/add-handler #'render))
  (render))

