(ns clojure.reflect
  (:require [clojure.browser.net :as net]
            [clojure.browser.event :as event]))

(defn evaluate-javascript [block]
  (let [result (try (js* "eval(~{block})")
                    (catch js/Error e
                      (.log js/console e)))]
    result))

(defn query-meta [sym cb]
  (let [conn (net/xhr-connection)]
    (event/listen conn :success (fn [e]
                                  (let [js (.getResponseText e/currentTarget ())]
                                    (cb (evaluate-javascript js)))))
    ;; (event/listen conn :error #(reset! sym-meta :request-failed))
    (net/transmit conn (str "/reflect?var=" (js/encodeURIComponent (str sym))))))

(defn doc [{:keys [name method-params doc]}]
  (when-not (empty? name)
    (println name)
    (println "(" method-params ")")
    (println doc)))
