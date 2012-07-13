(ns clojure.reflect
  (:require [clojure.browser.net :as net]
            [clojure.browser.event :as event]))

(deftype Var [meta]
  IMeta
  (-meta [this] @meta))

(defn evaluate-javascript [block]
  (let [result (try (js* "eval(~{block})")
                    (catch js/Error e
                      (.log js/console e)))]
    result))

(defn var [sym]
  (let [conn (net/xhr-connection)
        sym-meta (atom :awaiting)]
    (event/listen conn :success (fn [e]
                                  (let [js (.getResponseText e/currentTarget ())]
                                    (reset! sym-meta (evaluate-javascript js)))))
    (event/listen conn :error #(reset! sym-meta :request-failed))
    (net/transmit conn (str "/reflect?var=" (js/encodeURIComponent (str sym))))
    (Var. sym-meta)))
