(ns hughpowell.co.uk.phoenix.bank-of-scotland-specification
  (:require [clojure.core.async :as async]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [clojure.test :refer [testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as check-properties]
            [datahike.api :as datahike]
            [duct.core :as duct]
            [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser :as bank-of-scotland-csv-parser]))

(duct/load-hierarchy)

(defmethod ig/init-key :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel [_key _opts]
  (async/chan))

(defn- ->date-string [instant]
  (java-time/format
    "dd/MM/yyyy"
    (-> instant
        java-time/instant
        (java-time/zoned-date-time "Europe/London"))))

(defn- sort-transactions [transactions]
  (sort-by (apply juxt
                  (fn [[date]]
                    (java-time/local-date "dd/MM/yyyy" date))
                  (take (dec (count (first transactions))) (drop 1 (map #(fn [c] (nth c %)) (range)))))
           transactions))

(defspec a-new-transaction-interval
         100
  (testing "is added to the database"
    (check-properties/for-all
      [transactions (gen/fmap
                      (fn [[header rows]] (->> rows
                                               sort-transactions
                                               (cons header)))
                      (gen/tuple
                        (spec/gen ::bank-of-scotland-csv-parser/csv-headers)
                        (spec/gen ::bank-of-scotland-csv-parser/csv-rows)))]
      (let [config (-> (duct/resource "hughpowell/co/uk/phoenix/config.edn")
                       (duct/read-config)
                       (duct/prep-config [:duct.profile/test]))
            sut (ig/init config)
            transaction-file (->> "BankOfScotlandTest.csv"
                                  (str (:bank-of-scotland/input-path sut))
                                  fs/file)]
        (try
          (let [{:keys [:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection
                        :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel
                        :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers]} sut
                ten-seconds (* 10 1000)]
            (with-open [writer (io/writer transaction-file)]
              (csv/write-csv writer transactions))
            (let [[result _channel] (async/alts!! [completion-channel (async/timeout ten-seconds)])]
              (when (instance? Throwable result)
                (throw result)))
            (let [db-transactions (->> @connection
                                       (datahike/q
                                         '[:find [(pull ?e [*]) ...]
                                           :where [?e :phoenix/institution :phoenix/bank-of-scotland]])
                                       (map
                                         (fn [t]
                                           (-> t
                                               (update :bank-of-scotland/date ->date-string)
                                               (update :bank-of-scotland/credit str)
                                               (update :bank-of-scotland/debit str)
                                               (update :bank-of-scotland/balance str)))))]
              (= (rest transactions)
                 (->> db-transactions
                      (map (fn [transaction]
                             (map #(get transaction %) (rest headers))))
                      sort-transactions))))
          (finally
            (io/delete-file transaction-file true)
            (datahike/delete-database (:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection config))
            (ig/halt! sut)))))))

(defn spy [data]
  (clojure.pprint/pprint data)
  data)

(defspec a-duplicate-transaction-interval
         100
  (testing "is added idempotentically"
    (check-properties/for-all
      [transactions (gen/fmap
                      (fn [[header rows]] (->> rows
                                               sort-transactions
                                               (cons header)))
                      (gen/tuple
                        (spec/gen ::bank-of-scotland-csv-parser/csv-headers)
                        (spec/gen ::bank-of-scotland-csv-parser/csv-rows)))]
      (let [config (-> (duct/resource "hughpowell/co/uk/phoenix/config.edn")
                       (duct/read-config)
                       (duct/prep-config [:duct.profile/test]))
            sut (ig/init config)
            transaction-file (->> "BankOfScotlandTest.csv"
                                  (str (:bank-of-scotland/input-path sut))
                                  fs/file)]
        (try
          (let [{:keys [:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection
                        :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel
                        :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers]} sut
                ten-seconds (* 10 1000)]
            (with-open [writer (io/writer transaction-file)]
              (csv/write-csv writer transactions))
            (let [[result _channel] (async/alts!! [completion-channel (async/timeout ten-seconds)])]
              (when (instance? Throwable result)
                (throw result)))
            (let [db-transactions (->> @connection
                                       (datahike/q
                                         '[:find [(pull ?e [*]) ...]
                                           :where [?e :phoenix/institution :phoenix/bank-of-scotland]])
                                       (sort-by (apply juxt headers))
                                       doall)]
              (fs/delete transaction-file)
              (with-open [writer (io/writer transaction-file)]
                (csv/write-csv writer transactions))
              (let [[result _channel] (async/alts!! [completion-channel (async/timeout ten-seconds)])]
                (fs/delete transaction-file)
                (when (instance? Throwable result)
                  (throw result))
                (let [idempotent-db-transactions (->> @connection
                                                      (datahike/q
                                                        '[:find [(pull ?e [*]) ...]
                                                          :where [?e :phoenix/institution :phoenix/bank-of-scotland]])
                                                      (sort-by (apply juxt headers))
                                                      doall)]

                  (= db-transactions idempotent-db-transactions)))))
          (catch Throwable t
            (.printStackTrace t)
            (throw t))
          (finally
            (fs/delete transaction-file)
            (datahike/delete-database (:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection config))
            (ig/halt! sut)))))))