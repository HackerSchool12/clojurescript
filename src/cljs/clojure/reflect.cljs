(ns clojure.reflect
  (:require [clojure.browser.net :as net]
            [clojure.browser.event :as event]))

(defn test-conn []
  (let [conn (net/xhr-connection)]
    (event/listen conn :success (fn [e] (.log js/console e)))
    (net/transmit conn "/test")))
