(ns cljs.repl.reflect
  (:require [cljs.repl.server :as server]))

(server/dispatch-on :get
                    (fn [{:keys [path]} _ _] (.startsWith path "/test"))
                    (fn [request conn opts]
                      (server/send-and-close
                        conn
                        200
                        "Success")))
