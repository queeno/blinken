(ns govuk.blinken.sensu-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [govuk.blinken.sensu :as sensu]
            [govuk.blinken.protocols :as protocols]))


(def example-hosts-json
  (json/parse-string (slurp (io/resource "fixtures/sensu/hosts.json")) true))
(def example-alerts-json
  (json/parse-string (slurp (io/resource "fixtures/sensu/alerts.json")) true))

(deftest test-parse-hosts
  (testing "converts hosts to simple map"
    (is (= (sensu/parse-hosts example-hosts-json)
           {:up ["logs-elasticsearch-1.localdomain" "backend-app-2.localdomain"]
            :down []}))))

(deftest test-parse-alerts
  (testing "converts alerts to simple map"
    (is (= (sensu/parse-alerts example-alerts-json)
           {:critical [{:host "backend-app-1.localdomain"
                        :name "backdrop_buckets_health_check"
                        :info "CheckHTTP CRITICAL: 500\n"}
                       {:host "backend-app-2.localdomain"
                        :name "backdrop_buckets_health_check"
                        :info "CheckHTTP CRITICAL: 500\n"}]
            :warning  [{:host "monitoring-1.localdomain"
                        :name "check_low_disk_space_monitoring-1"
                        :info "CheckGraphiteData WARNING: check_low_disk_space_monitoring-1 has passed warning threshold (2830925824.0)\n"}]
            :ok       []
            :unknown  []}))))

(deftest test-create
  (testing "it creates a service"
    (let [service (sensu/create "http://foo" {})]
      (is (= (protocols/get-status service)
             {:hosts {:up [] :down []}
              :alerts {:critical [] :warning [] :ok [] :unknown []}})))))



