(ns hughpowell.co.uk.phoenix.bank-of-scotland-specification
  (:require [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [clojure.test :refer [testing is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as check-generators]
            [clojure.test.check.properties :as check-properties]
            [datahike.api :as datahike]
            [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer :as transaction-importer]
            [hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser :as bank-of-scotland-csv-parser]
            [hughpowell.co.uk.phoenix.bank-of-scotland.storage :as bank-of-scotland-storage]
            [hughpowell.co.uk.phoenix.test-concerns.units :as units]
            [hughpowell.co.uk.phoenix.test-concerns.fixtures :as fixtures]
            [hughpowell.co.uk.phoenix.test-concerns.transactions :as transactions]))

(defmethod ig/init-key :bank-of-scotland/input-test-path [_key _opts]
  (fs/temp-dir "Phoenix-BankOfScotland-Test"))

(defmethod ig/halt-key! :bank-of-scotland/input-test-path [_key value]
  (fs/delete-dir value))

(defn db-transactions->sorted-rows [connection headers]
  (->> @connection
       (datahike/q
         '[:find [(pull ?e [*]) ...]
           :where [?e :phoenix/institution :phoenix/bank-of-scotland]])
       (map
         (fn [t]
           (-> t
               (update :bank-of-scotland/date #(units/->date-string "dd/MM/yyyy" %))
               (update :bank-of-scotland/credit units/->money-string)
               (update :bank-of-scotland/debit units/->money-string)
               (update :bank-of-scotland/balance units/->money-string))))
       (map (fn [transaction]
              (map #(get transaction %) (rest headers))))
       (transactions/sort "dd/MM/yyyy" 0)))

(defn a-new-transaction-interval-is-added-to-the-database [transactions]
  (fixtures/with-sut
    sut
    (let [{:keys [::storage/connection
                  ::transaction-importer/completion-channel
                  ::bank-of-scotland-storage/headers
                  :bank-of-scotland/input-path]} sut]
      (transactions/write input-path "BankOfScotlandTest.csv" transactions)
      (fixtures/complete completion-channel)
      (is (= (rest transactions)
             (db-transactions->sorted-rows connection headers))))))

(defspec a-new-transaction-interval
         100
  (testing "is added to the database"
    (check-properties/for-all
      [transactions (transactions/generator "dd/MM/yyyy" 0 ::bank-of-scotland-csv-parser/csv-headers ::bank-of-scotland-csv-parser/csv-rows)]
      (a-new-transaction-interval-is-added-to-the-database transactions))))

(defn duplicate-transaction-intervals-are-added-idempotently [transactions number-of-duplicates]
  (fixtures/with-sut
    sut
    (let [{:keys [::storage/connection
                  ::transaction-importer/completion-channel
                  ::bank-of-scotland-storage/headers
                  :bank-of-scotland/input-path]} sut]
      (is (apply =
                 (repeatedly
                   number-of-duplicates
                   (fn []
                     (transactions/write input-path "BankOfScotlandTest.csv" transactions)
                     (fixtures/complete completion-channel)
                     (->> @connection
                          (datahike/q
                            '[:find [(pull ?e [*]) ...]
                              :where [?e :phoenix/institution :phoenix/bank-of-scotland]])
                          (sort-by (apply juxt headers))
                          doall))))))))

(defspec duplicate-transaction-intervals
         100
  (testing "are added idempotentically"
    (check-properties/for-all
      [transactions (transactions/generator "dd/MM/yyyy" 0 ::bank-of-scotland-csv-parser/csv-headers ::bank-of-scotland-csv-parser/csv-rows)
       number-of-duplicates (check-generators/choose 2 10)]
      (duplicate-transaction-intervals-are-added-idempotently transactions number-of-duplicates))))

(defn later-overlapping-transaction-intervals-take-precedence [transaction-intervals]
  (fixtures/with-sut
    sut
    (let [{:keys [::storage/connection
                  ::transaction-importer/completion-channel
                  ::bank-of-scotland-storage/headers
                  :bank-of-scotland/input-path]} sut]
      (doall (map (fn [transaction-interval]
                    (transactions/write input-path "BankOfScotlandTest.csv" transaction-interval)
                    (fixtures/complete completion-channel))
                  transaction-intervals))
      (let [expected (transactions/sort
                       "dd/MM/yyyy"
                       0
                       (reduce
                         (fn
                           ([] [])
                           ([acc item]
                            (concat (take-while #(java-time/before? (units/->local-date #"\d{2}\/\d{2}\/\d{4}" "dd/MM/yyyy" (first %))
                                                                    (units/->local-date #"\d{2}\/\d{2}\/\d{4}" "dd/MM/yyyy" (ffirst item)))
                                                acc)
                                    item)))
                         (map rest transaction-intervals)))
            results (db-transactions->sorted-rows connection headers)]
        (is (= expected results))))))

(defspec later-overlapping-transaction-intervals
         100
  (testing "take precedence"
    (check-properties/for-all
      [transaction-intervals (gen/fmap
                               (fn [[headers rows]]
                                 (->> rows
                                      (transactions/sort "dd/MM/yyyy" 0)
                                      (partition-all 3 2)
                                      (map #(cons headers %))))
                               (gen/tuple
                                 (spec/gen ::bank-of-scotland-csv-parser/csv-headers)
                                 (spec/gen ::bank-of-scotland-csv-parser/csv-rows)))]
      (later-overlapping-transaction-intervals-take-precedence transaction-intervals))))
