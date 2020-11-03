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
            [hughpowell.co.uk.phoenix.test-concerns.dates :as dates]
            [hughpowell.co.uk.phoenix.test-concerns.fixtures :as fixtures]
            [hughpowell.co.uk.phoenix.test-concerns.transactions :as transactions]))

(defmethod ig/init-key :bank-of-scotland/input-path [_key _opts]
  (fs/temp-dir "Phoenix-BankOfScotland-Test"))

(defmethod ig/halt-key! :bank-of-scotland/input-path [_key value]
  (fs/delete-dir value))

(defn db-transactions->sorted-rows [connection headers]
  (->> @connection
       (datahike/q
         '[:find [(pull ?e [*]) ...]
           :where [?e :phoenix/institution :phoenix/bank-of-scotland]])
       (map
         (fn [t]
           (-> t
               (update :bank-of-scotland/date dates/->date-string)
               (update :bank-of-scotland/credit str)
               (update :bank-of-scotland/debit str)
               (update :bank-of-scotland/balance str))))
       (map (fn [transaction]
              (map #(get transaction %) (rest headers))))
       transactions/sort))

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
      [transactions (transactions/generator ::bank-of-scotland-csv-parser/csv-headers ::bank-of-scotland-csv-parser/csv-rows)]
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
      [transactions (transactions/generator ::bank-of-scotland-csv-parser/csv-headers ::bank-of-scotland-csv-parser/csv-rows)
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
                       (reduce
                         (fn
                           ([] [])
                           ([acc item]
                            (concat (take-while #(java-time/before? (dates/->local-date (first %))
                                                                    (dates/->local-date (ffirst item)))
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
                                      transactions/sort
                                      (partition-all 3 2)
                                      (map #(cons headers %))))
                               (gen/tuple
                                 (spec/gen ::bank-of-scotland-csv-parser/csv-headers)
                                 (spec/gen ::bank-of-scotland-csv-parser/csv-rows)))]
      (later-overlapping-transaction-intervals-take-precedence transaction-intervals))))

(comment
  (later-overlapping-transaction-intervals-take-precedence
    [[["Transaction Date"
       "Transaction Type"
       "Sort Code"
       "Account Number"
       "Transaction Description"
       "Debit Amount"
       "Credit Amount"
       "Balance"]
      ["01/01/2000" "0" "10-10-10" "00000000" "0" "0.00" "1.00" "1.00"]]]))