(ns govuk.blinken.service.polling
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.core.async :as async]
            [govuk.blinken.service :as service]))



(defn poll [ms func & args]
  (let [control (async/chan)
        times (atom 0)
        out (async/go (loop [[v ch] (async/alts! [(async/timeout 0) control])]
                        (if (= ch control)
                          @times
                          (do (swap! times inc)
                              (apply func args)
                              (recur (async/alts! [(async/timeout ms) control]))))))]
    {:control control :out out}))

(defn cancel-poll [chans]
  (async/>!! (:control chans) :cancel)
  (async/<!! (:out chans)))



(defn- handle-response [status-atom status-keyword parse-fn response]
  (cond (:error response)
        (println "Request error:" response)

        (= (:status response) 200)
        (swap! status-atom assoc status-keyword
               (parse-fn (json/parse-string (:body response) true)))

        :else
        (println "Unknown request error:" response)))

(defn- get-and-parse [base-url endpoint options status-atom status-key]
  (http/get (str base-url (:resource endpoint)) options
            (partial handle-response status-atom
                     status-key (:parse-fn endpoint))))


(deftype PollingService [url poller-options user-options status-atom poller-atom]
  service/Service
  (start [this] (let [poll-ms (get user-options :poll-ms 1000)
                      http-options (get user-options :http {})]
                  (println (str "Starting poller [ms:" poll-ms ", url:" url "]"))
                  (reset! poller-atom
                          (poll poll-ms
                                     (fn [status-atom]
                                       (get-and-parse url (:alerts poller-options)
                                                      http-options status-atom :alerts)
                                       (get-and-parse url (:hosts poller-options)
                                                      http-options status-atom :hosts))
                                     status-atom))))
  (get-status [this] @status-atom)
  (stop [this] (if-let [poller @poller-atom]
                 (do (cancel-poll poller)
                     (reset! poller-atom nil)
                     (println "Killed poller")))))

(defn create [url poller-options user-options]
  (let [status-atom (atom {:hosts  {:up [] :down []}
                           :alerts {:critical [] :warning [] :ok [] :unknown []}})]
    (PollingService. url poller-options user-options status-atom (atom nil))))


