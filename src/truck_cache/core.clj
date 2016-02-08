(ns truck-cache.core
  (:require
   ;; web
   [org.httpkit.server :refer (run-server)]
   [compojure.core :refer (routes GET POST)]
   [compojure.handler :as handler]
   [ring.middleware.logger :refer (wrap-with-logger)]
   [ring.middleware.params :refer (wrap-params)]
   ;; get
   [org.httpkit.client :as http]
   [clojure.walk :refer (keywordize-keys stringify-keys)]
   ;; misc
   [environ.core :refer (env)]
   ;; dev
   [clojure.tools.namespace.repl :refer (refresh)]
   ))


(defn status-ok? [status]
  (and (>= status 200) (< status 300)))

(defn fetch-from-backend [state params]
  (let [url (:remote-url state)
        timeout (:remote-timeout state)]
    ;; we will issue request and wait for @promise to be fullfilled, since
    ;; it's easier to understand
    (let [{:keys [status error body headers]} @(http/get url
                                                         {:timeout timeout
                                                          :query-params params
                                                          :follow-redirects true})]
      (when error
        ;; report any errors (timeouts and io errors), since they
        ;; must handled differently
        (throw error))
      ;; and proxy response
      {:body body
       :status status
       ;; since we don't know, whether underlying web-server wants to
       ;; use gzip or not, and backend server may respond with gzip
       ;; (which will be handled by http-kit/client), we will delete
       ;; content-encoding header, since it may confuse browsers.
       ;; We also have to stringify keys in headers map
       :headers (-> headers (dissoc :content-encoding) stringify-keys)})))


(defn with-cache* [cache cache-key should-save-fn? eval-fn]
  (if-let [cached (@cache cache-key)]
    ;; found, respond straight away
    cached
    ;; otherwise, use supplied forms
    (let [result (eval-fn)]
      (when (should-save-fn? result)
        ;; cache the successful response...
        ;; XXX: ideally, we should obey Expires or E-Tag headers in response
        ;; but since backend server always responds with Date equal Expires
        ;; we will just cache stuff forever.
        ;; This is also simplifies implementation.
        (swap! cache assoc cache-key result))
      result)))

(defmacro with-cache [cache cache-key should-save-fn? & forms]
  "Returns cached value from *cache* for given *cache-key*. If nothing present,
evaluates *forms* and if *should-save-fn?* returns true for result, caches it."
  `(with-cache* ~cache ~cache-key ~should-save-fn? (fn [] ~@forms)))

(defn geocode [state]
  (fn [req]
    (let [qp (:query-params req)]
      (try
        (with-cache (:cache state) qp #(status-ok? (:status %))
          (fetch-from-backend state qp))
        (catch org.httpkit.client.TimeoutException e
          {:status 504
           :body "Upstream service has timed out!"})
        (catch Exception e
          {:status 503
           :body (str "Error: " (.getMessage e))})))))

;; Don't use defroutes, since we want to pass state to our handlers
;; Passing explicit state can be somewhat cumbersome, but simplify repl
;; development and testing
(defn app-routes [state]
  (routes
   (GET "/geocode" [] (geocode state))))

(def server nil)

(defn create-state []
  {:cache (atom {})
   ;; http-kit client uses one 'timeout' parameter as both
   ;; connection and read timeouts. So, since our clients
   ;; will wait no more than two seconds, we use 1000ms
   ;; limit to protect against worst case.
   :remote-timeout 1000
   :remote-url (env :remote-url)})

(defn start-app []
  (when (nil? server)
    (org.apache.log4j.BasicConfigurator/configure)
    (alter-var-root #'server (constantly
                              (run-server
                               (->
                                (create-state)
                                app-routes
                                wrap-with-logger
                                wrap-params)
                               {:port 3344})))))

(defn stop-app []
  (when-not (nil? server)
    (println "Stop")
    (server :timeout 100)
    (alter-var-root #'server (constantly nil))))

(defn restart []
  "Stops running server, if any, refreshes namespace, and starts new one."
  (stop-app)
  (refresh :after 'truck-cache.core/start-app))

(defn -main [& args]
  (println "Started...")
  (start-app))
