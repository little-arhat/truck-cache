(ns truck-cache.core-test
  (:require [clojure.test :refer :all]
            [org.httpkit.client :as http]
            [truck-cache.core :refer :all]))

(defn- ist [form & msg]
  (is (= true form) msg))

(defn- isf [form & msg]
  (is (= false form) msg))

(defn- equal [a b & msg]
  (is (= a b) msg))


(deftest test-state-management
  (testing "newly created state should contain empty cache"
    (equal (count @(:cache (create-state)))
           0))
  (testing "newly created state should contain required keys"
    (let [state (create-state)]
      (ist (contains? state :cache))
      (ist (contains? state :remote-timeout))
      (ist (contains? state :remote-url)))))

(deftest test-cache-management
  (testing "Should return value for existing key and leave cache as it is"
    (let [cache (atom {:test 42})]
      (equal (count @cache)
             1)
      (equal (with-cache cache :test (constantly false) false)
             42)
      (equal (count @cache)
             1)))
  (testing "Should calculate value for missing key"
    (equal (with-cache (atom {}) :test (constantly false) 42)
           42))
  (testing "Should save evaluated value if need to do so"
    (let [cache (atom {})]
      (equal (count @cache) 0)
      (with-cache cache :test (constantly true) 42)
      (equal (count @cache) 1)
      (ist (contains? @cache :test))))
  (testing "Should not save evaluated value if there is no need to do so"
    (let [cache (atom {})]
      (equal (count @cache) 0)
      (with-cache cache :test (constantly false) 42)
      (equal (count @cache) 0)
      (isf (contains? @cache :test)))))

;; proper macro for mocking, instead of clumsy with-redefs-fn
(defmacro with-mocked-result [name ret & body]
  `(with-redefs-fn {~name (fn [& args#] ~ret)}
     (fn [] ~@body)))

(deftest test-remote-fetch
  (testing "Should save successful response from backend"
    ;; since http/get returns promise, emulate it with another Derefable -- atom
    (with-mocked-result #'http/get (atom {:status 200 :body "" :headers {} :error nil})
      (let [state (create-state)
            handler (geocode state)]
        (equal (count @(:cache state))
               0)
        (handler {:query-params {"address" "Moscow"}})
        (equal (count @(:cache state))
               1)
        (ist (contains? @(:cache state) {"address" "Moscow"} )))))
  (testing "Should not save errors from backend"
    (with-mocked-result #'http/get (atom {:status 404 :body "" :headers {} :error nil})
      (let [state (create-state)
            handler (geocode state)]
    (equal (count @(:cache state))
           0)
    (handler {:query-string "address=Moscow"})
    (equal (count @(:cache state))
           0))))
  (testing "Handler should return 503 for any unexpected errors"
    (with-mocked-result #'http/get (atom {:error (Exception. "some error")})
      (let [state (create-state)
            handler (geocode state)
            resp (handler {})]
        (equal (:status resp) 503))))
  (testing "Handler should return 504 for timeouts"
    ;; we will return error from http/get and fetch-from-backend will rethrow
    ;; it for us
    (with-mocked-result #'http/get (atom {:error (org.httpkit.client.TimeoutException. "timeout")})
      (let [state (create-state)
            handler (geocode state)
            resp (handler {})]
        (equal (:status resp) 504)))))
