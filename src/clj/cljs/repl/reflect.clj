(ns cljs.repl.reflect
  (:require [cljs.repl.server :as server]))

(server/dispatch-on :get
                    (fn [{:keys [path]} _ _] (= path "/test"))
                    (fn [request conn opts]
                      (println "in the test.")
                      (server/send-and-close
                        conn
                        200
                        "Success")))
