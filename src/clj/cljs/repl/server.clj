(ns cljs.repl.server
  (:refer-clojure :exclude [loaded-libs])
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [cljs.compiler :as comp]
            [cljs.closure :as cljsc]
            [cljs.repl :as repl])
  (:import java.io.BufferedReader
           java.io.BufferedWriter
           java.io.InputStreamReader
           java.io.OutputStreamWriter
           java.net.Socket
           java.net.ServerSocket
           cljs.repl.IJavaScriptEnv))

(defonce state (atom {:socket nil
                      :connection nil
                      :promised-conn nil
                      :return-value-fn nil
                      :client-js nil}))

(defonce handlers (atom {:get []
                         :post []}))

(defn register-handler
  ; TODO fix shitty docstring
  "Dispatches to the first method handler for which pred returns true."
  ([method pred handler]
     (register-handler method {:pred pred :handler handler}))
  ([method {:as m}]
     (swap! handlers (fn [old]
                       (assoc old method (conj (method old) m))))))

;;; assumes first line already consumed
(defn parse-headers
  "Parse the headers of an HTTP POST request."
  [header-lines]
  (apply hash-map
   (mapcat
    (fn [line]
      (let [[k v] (str/split line #":" 2)]
        [(keyword (str/lower-case k)) (str/triml v)]))
    header-lines)))

(defn read-headers [rdr]
  (loop [next-line (.readLine rdr)
         header-lines []]
    (if (= "" next-line)
      header-lines                      ;we're done reading headers
      (recur (.readLine rdr) (conj header-lines next-line)))))

(defn read-post [line rdr]
  (let [[_ path _] (str/split line #" ")
        headers (parse-headers (read-headers rdr))
        content-length (Integer/parseInt (:content-length headers))
        content (char-array content-length)]
    (io! (.read rdr content 0 content-length)
         {:method :post
          :path path
          :headers headers
          :content (String. content)})))

(defn read-get [line rdr]
  (let [[_ path _] (str/split line #" ")
        headers (parse-headers (read-headers rdr))]
    {:method :get
     :path path
     :headers headers}))

(defn read-request [rdr]
  (let [line (.readLine rdr)]
    (cond (.startsWith line "POST") (read-post line rdr)
          (.startsWith line "GET") (read-get line rdr)
          :else {:method :unknown :content line})))

(defn- status-line [status]
  (case status
    200 "HTTP/1.1 200 OK"
    404 "HTTP/1.1 404 Not Found"
    "HTTP/1.1 500 Error"))

(defn send-and-close
  "Use the passed connection to send a form to the browser. Send a
  proper HTTP response."
  ([conn status form]
     (send-and-close conn status form "text/html"))
  ([conn status form content-type]
     (let [utf-8-form (.getBytes form "UTF-8")
           content-length (count utf-8-form)
           headers (map #(.getBytes (str % "\r\n"))
                        [(status-line status)
                         "Server: ClojureScript REPL"
                         (str "Content-Type: "
                              content-type
                              "; charset=utf-8")
                         (str "Content-Length: " content-length)
                         ""])]
       (with-open [os (.getOutputStream conn)]
         (do (doseq [header headers]
               (.write os header 0 (count header)))
             (.write os utf-8-form 0 content-length)
             (.flush os)
             (.close conn))))))

(defn send-404 [conn path]
  (send-and-close conn 404
                  (str "<html><body>"
                       "<h2>Page not found</h2>"
                       "No page " path " found on this server."
                       "</body></html>")
                  "text/html"))

(defn dispatch-request [request opts conn]
  (if-let [handlers ((:method request) @handlers)]
    (if-let [handler (some (fn [{:keys [pred handler]}]
                             (when (pred opts conn request)
                               handler)) handlers)]
      (if (= :post (:method request))
        (handler conn (read-string (:content request)))
        (handler opts conn request))
      (send-404 conn (:path request)))
    (.close conn)))

(defn handle-connection
  [opts conn]
  (let [rdr (BufferedReader. (InputStreamReader. (.getInputStream conn)))]
    (if-let [request (read-request rdr)]
      (dispatch-request request opts conn)
      (.close conn))))

(defn server-loop
  [opts server-socket]
  (let [conn (.accept server-socket)]
    (do (.setKeepAlive conn true)
        (future (handle-connection opts conn))
        (recur opts server-socket))))

(defn start-server
  "Start the server on the specified port."
  [opts]
  (let [ss (ServerSocket. (:port opts))]
    (future (server-loop opts ss))
    (swap! state (fn [old] (assoc old :socket ss :port (:port opts))))))

(defn stop-server
  []
  (.close (:socket @state)))
