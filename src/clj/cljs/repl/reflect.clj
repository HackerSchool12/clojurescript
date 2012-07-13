(ns cljs.repl.reflect
  (:require [cljs.repl.server :as server]
            [cljs.analyzer :as analyzer]
            [cljs.compiler :as comp]
            [clojure.string :as str]))

(defn- dissoc-unless
  "Dissoc all keys from map that do not appear in key-set.

    (dissoc-unless {:foo 1 :bar 2} #{:foo})
    => {:foo 1}"
  [m key-set]
  {:pre [(map? m)
         (set? key-set)]}
  (reduce (fn [coll key]
            (if (contains? key-set key)
              coll
              (dissoc coll key)))
          m (keys m)))

(defn- get-meta [sym]
  (let [ns (symbol (namespace sym))
        n  (symbol (name sym))]
    (if-let [sym-meta (get (:defs (get @analyzer/namespaces ns)) n)]
      (dissoc-unless sym-meta
                     #{:name :arglists :doc :line :file}))))

(defn- get-var [{:keys [content]}]
  (let [content (read-string content)]
    (-get-meta content)))

(defn- url-decode [encoded & [encoding]]
  (java.net.URLDecoder/decode encoded (or encoding "UTF-8")))

(server/dispatch-on :get
                    (fn [{:keys [path]} _ _] (.startsWith path "/reflect"))
                    (fn [{:keys [path]} conn opts]
                      (let [sym (-> (str/split path #"=")
                                    (last)
                                    (url-decode)
                                    (read-string))
                            ast (analyzer/analyze {:ns {:name 'cljs.user}}
                                                  ;; Replace with -get-meta
                                                  {:item (str sym)})
                            js  (comp/emit-str ast)]
                        (server/send-and-close conn 200 js "text/javascript"))))
