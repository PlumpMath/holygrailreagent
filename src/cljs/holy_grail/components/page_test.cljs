(ns holy-grail.components.page-test
  (:require ; [holy-grail.components.page :as app.page]
            [cljs.test :refer-macros [is]]
            [devcards.core :as dc :refer-macros [defcard-rg deftest]]
            [reagent.core]))
(defn component []
  "Application page component."
  [:div "Hello world"])


(defcard-rg page
  "This is the `app/page` component."
  [component])

(deftest page-test)

(defn devcards []
  (dc/start-devcard-ui!))
