(ns holy-grail.components.page-test
  (:require ; [holy-grail.components.page :as app.page]
            [cljs.test :refer-macros [is]]
            [devcards.core :as dc :refer-macros [defcard-rg deftest]]
            [reagent.core]))


(defcard-rg page
  "This is the `app/page` component."
  [:div
   [:p "Hello world. I totes love you. Yeah!!"]
   [:p "What now fool?"]])

(defcard-rg buttongguy
    [:button {:on-click #(js/console.log "Log me in, maybe?")}
       "Are we logged in? Or maybe not?"])


(defn devcards []
  (dc/start-devcard-ui!))
