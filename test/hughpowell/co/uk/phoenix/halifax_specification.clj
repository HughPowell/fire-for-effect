(ns hughpowell.co.uk.phoenix.halifax-specification
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
            [hughpowell.co.uk.phoenix.halifax.csv-parser :as halifax-csv-parser]
            [hughpowell.co.uk.phoenix.halifax.storage :as halifax-storage]
            [hughpowell.co.uk.phoenix.test-concerns.units :as units]
            [hughpowell.co.uk.phoenix.test-concerns.fixtures :as fixtures]
            [hughpowell.co.uk.phoenix.test-concerns.transactions :as transactions]))

(defmethod ig/init-key :halifax/input-test-path [_key _opts]
  (fs/temp-dir "Phoenix-Halifax-Test"))

(defmethod ig/halt-key! :halifax/input-test-path [_key value]
  (fs/delete-dir value))

(defn db-transactions->sorted-rows [connection headers]
  (->> @connection
       (datahike/q
         '[:find [(pull ?e [*]) ...]
           :where [?e :phoenix/institution :phoenix/halifax]])
       (map
         (fn [t]
           (-> t
               (update :halifax/date #(units/->date-string "dd/MM/yyyy" %))
               (update :halifax/date-entered #(units/->date-string "dd/MM/yyyy" %))
               (update :halifax/amount units/->money-string))))
       (map (fn [transaction]
              (map #(get transaction %) (rest headers))))
       (transactions/sort "dd/MM/yyyy")))

(defn a-new-transaction-interval-is-added-to-the-database [transactions]
  (fixtures/with-sut
    sut
    (let [{:keys [::storage/connection
                  ::transaction-importer/completion-channel
                  ::halifax-storage/headers
                  :halifax/input-path]} sut]
      (transactions/write input-path "HalifaxTest.csv" transactions)
      (fixtures/complete completion-channel)
      (is (= (rest (map drop-last transactions))
             (db-transactions->sorted-rows connection headers))))))

(defspec a-new-transaction-interval
         100
         (testing "is added to the database"
           (check-properties/for-all
             [transactions (transactions/generator "dd/MM/yyyy" ::halifax-csv-parser/csv-headers ::halifax-csv-parser/csv-rows)]
             (a-new-transaction-interval-is-added-to-the-database transactions))))

(defn duplicate-transaction-intervals-are-added-idempotently [transactions number-of-duplicates]
  (fixtures/with-sut
    sut
    (let [{:keys [::storage/connection
                  ::transaction-importer/completion-channel
                  ::halifax-storage/headers
                  :halifax/input-path]} sut]
      (is (apply =
                 (repeatedly
                   number-of-duplicates
                   (fn []
                     (transactions/write input-path "HalifaxTest.csv" transactions)
                     (fixtures/complete completion-channel)
                     (->> @connection
                          (datahike/q
                            '[:find [(pull ?e [*]) ...]
                              :where [?e :phoenix/institution :phoenix/halifax]])
                          (sort-by (apply juxt headers))
                          doall))))))))

(defspec duplicate-transaction-intervals
         100
         (testing "are added idempotentically"
           (check-properties/for-all
             [transactions (transactions/generator "dd/MM/yyyy" ::halifax-csv-parser/csv-headers ::halifax-csv-parser/csv-rows)
              number-of-duplicates (check-generators/choose 2 10)]
             (duplicate-transaction-intervals-are-added-idempotently transactions number-of-duplicates))))

(defn later-overlapping-transaction-intervals-take-precedence [transaction-intervals]
  (fixtures/with-sut
    sut
    (let [{:keys [::storage/connection
                  ::transaction-importer/completion-channel
                  ::halifax-storage/headers
                  :halifax/input-path]} sut]
      (doall (map (fn [transaction-interval]
                    (transactions/write input-path "HalifaxTest.csv" transaction-interval)
                    (fixtures/complete completion-channel))
                  transaction-intervals))
      (let [expected (transactions/sort
                       "dd/MM/yyyy"
                       (reduce
                         (fn
                           ([] [])
                           ([acc item]
                            (concat (take-while #(java-time/before? (units/->local-date #"\d{2}\/\d{2}\/\d{4}" "dd/MM/yyyy" (first %))
                                                                    (units/->local-date #"\d{2}\/\d{2}\/\d{4}" "dd/MM/yyyy" (ffirst item)))
                                                acc)
                                    item)))
                         (map (fn [[_ & transactions]]
                                (map drop-last transactions))
                              transaction-intervals)))
            results (db-transactions->sorted-rows connection headers)]
        (is (= expected results))))))

(defspec later-overlapping-transaction-intervals
         100
         (testing "take precedence"
           (check-properties/for-all
             [transaction-intervals (gen/fmap
                                      (fn [[headers rows]]
                                        (->> rows
                                             (transactions/sort "dd/MM/yyyy")
                                             (partition-all 3 2)
                                             (map #(cons headers %))))
                                      (gen/tuple
                                        (spec/gen ::halifax-csv-parser/csv-headers)
                                        (spec/gen ::halifax-csv-parser/csv-rows)))]
             (later-overlapping-transaction-intervals-take-precedence transaction-intervals))))
