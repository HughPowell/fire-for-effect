(ns hughpowell.co.uk.phoenix.bank-of-scotland-specification
  (:require [clojure.core.async :as async]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [clojure.test :refer [testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as check-properties]
            [hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser :as bank-of-scotland-csv-parser]
            [duct.core :as duct]
            [integrant.core :as ig]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [datahike.api :as datahike])
  (:import (java.time ZoneId)))

(duct/load-hierarchy)

(defmethod ig/init-key :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel [_key _opts]
  (async/chan))

(defn- ->local-date-string [instant]
  (java-time/format
    "dd/MM/yyyy"
    (-> instant
        java-time/instant
        (java-time/zoned-date-time (ZoneId/systemDefault))
        java-time/local-date)))

(defn- sort-transactions [transactions]
  (sort-by (apply juxt
                  (fn [[date]]
                    (java-time/local-date "dd/MM/yyyy" date))
                  (take (dec (count (first transactions))) (drop 1 (map #(fn [c] (nth c %)) (range)))))
           transactions))

(defspec a-new-transaction-period
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
                                  io/file)]
        (try
          (->> transactions
               (map (fn [row] (string/join "," row)))
               (string/join "\n")
               (spit transaction-file))
          (let [{:keys [:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection
                        :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel
                        :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers]} sut]
            (let [[result _channel] (async/alts!!
                                      [completion-channel
                                       (async/timeout (* 10 1000))])]
              (when (instance? Throwable result)
                (throw result)))
            (let [db-transactions (->> @connection
                                       (datahike/q
                                         '[:find [(pull ?e [*]) ...]
                                           :where [?e :phoenix/institution :phoenix/bank-of-scotland]])
                                       (map
                                         (fn [t]
                                           (-> t
                                               (update :bank-of-scotland/date ->local-date-string)
                                               (update :bank-of-scotland/credit str)
                                               (update :bank-of-scotland/debit str)
                                               (update :bank-of-scotland/balance str)))))]
              (= (rest transactions)
                 (sort-transactions (map (fn [transaction]
                                           (map #(get transaction %) (rest headers)))
                                         db-transactions)))))
          (finally
            (io/delete-file transaction-file true)
            (datahike/delete-database (:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection config))
            (ig/halt! sut)))))))

(defspec the-same-transaction-period
         (testing "added to the database twice "))