(ns holy-grail.components.page-test
  (:require [holy-grail.components.page :as app.page]
            [cljs.test :refer-macros [is]]
            [devcards.core :as dc :refer-macros [defcard-rg deftest]]
            [reagent.core]))

(defcard-rg page
  "This is the `app/page` component."
  [app.page/component])

(defn devcards []
  (dc/start-devcard-ui!))

(deftest page-test)
