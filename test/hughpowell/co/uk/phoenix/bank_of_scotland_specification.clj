(ns hughpowell.co.uk.phoenix.bank-of-scotland-specification
  (:require [clojure.core.async :as async]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [clojure.test :refer [testing is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as check-properties]
            [clojure.test.check.generators :as check-generators]
            [datahike.api :as datahike]
            [duct.core :as duct]
            [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser :as bank-of-scotland-csv-parser]))

(duct/load-hierarchy)

(defmethod ig/init-key :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel [_key _opts]
  (async/chan 10))

(defmethod ig/init-key :bank-of-scotland/input-path [_key _opts]
  (fs/temp-dir "Phoenix-BankOfScotland-Test"))

(defmethod ig/halt-key! :bank-of-scotland/input-path [_key value]
  (fs/delete-dir value))

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

(def transaction-generator (gen/fmap
                             (fn [[header rows]] (->> rows
                                                      sort-transactions
                                                      (cons header)))
                             (gen/tuple
                               (spec/gen ::bank-of-scotland-csv-parser/csv-headers)
                               (spec/gen ::bank-of-scotland-csv-parser/csv-rows))))

(defmacro with-resource [binding close-fn & body]
  `(let ~binding
     (try
       (do ~@body)
       (finally
         (~close-fn ~(binding 0))))))

(defmacro with-sut [sym & body]
  `(let [config# (-> (duct/resource "hughpowell/co/uk/phoenix/config.edn")
                     (duct/read-config)
                     (duct/prep-config [:duct.profile/test]))
         clean-up# (fn [sut#]
                     (ig/halt! sut#)
                     (datahike/delete-database (:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection config#)))]
     (with-resource [~sym (ig/init config#)] clean-up# ~@body)))

(defn write-transactions [input-path transactions]
  (with-open [writer (io/writer (fs/file input-path "BankOfScotlandTest.csv"))]
    (csv/write-csv writer transactions)))

(defn complete [channel]
  (let [one-second (* 1 1000)]
    (let [[result _channel] (async/alts!! [channel (async/timeout one-second)])]
      (when (instance? Throwable result)
        (throw result)))))

(defn db-transactions->sorted-rows [connection headers]
  (->> @connection
       (datahike/q
         '[:find [(pull ?e [*]) ...]
           :where [?e :phoenix/institution :phoenix/bank-of-scotland]])
       (map
         (fn [t]
           (-> t
               (update :bank-of-scotland/date ->date-string)
               (update :bank-of-scotland/credit str)
               (update :bank-of-scotland/debit str)
               (update :bank-of-scotland/balance str))))
       (map (fn [transaction]
              (map #(get transaction %) (rest headers))))
       sort-transactions))

(defn a-new-transaction-interval-is-added-to-the-database [transactions]
  (with-sut
    sut
    (let [{:keys [:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection
                  :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel
                  :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers
                  :bank-of-scotland/input-path]} sut]
      (write-transactions input-path transactions)
      (complete completion-channel)
      (is (= (rest transactions)
             (db-transactions->sorted-rows connection headers))))))

(defspec a-new-transaction-interval
         100
  (testing "is added to the database"
    (check-properties/for-all
      [transactions transaction-generator]
      (a-new-transaction-interval-is-added-to-the-database transactions))))

(defn duplicate-transaction-intervals-are-added-idempotently [transactions number-of-duplicates]
  (with-sut
    sut
    (let [{:keys [:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection
                  :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel
                  :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers
                  :bank-of-scotland/input-path]} sut]
      (is (apply =
                 (repeatedly
                   number-of-duplicates
                   (fn []
                     (write-transactions input-path transactions)
                     (complete completion-channel)
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
      [transactions transaction-generator
       number-of-duplicates (check-generators/choose 2 10)]
      (duplicate-transaction-intervals-are-added-idempotently transactions number-of-duplicates))))

(defn ->local-date [s]
  (->> s
       (re-find #"(\d{2})\/(\d{2})\/(\d{4})")
       rest
       (map #(Integer/parseInt %))
       reverse
       (apply java-time/local-date)))

(defn later-overlapping-transaction-intervals-take-precedence [transaction-intervals]
  (with-sut
    sut
    (let [{:keys [:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection
                  :hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/completion-channel
                  :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers
                  :bank-of-scotland/input-path]} sut]
      (doall (map (fn [transaction-interval]
                    (write-transactions input-path transaction-interval)
                    (complete completion-channel))
                  transaction-intervals))
      (let [expected (sort-transactions
                       (reduce
                         (fn
                           ([] [])
                           ([acc item]
                            (concat (take-while #(java-time/before? (->local-date (first %)) (->local-date (ffirst item))) acc) item)))
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
                                      sort-transactions
                                      (partition-all 3 2)
                                      (map #(cons headers %))))
                               (gen/tuple
                                 (spec/gen ::bank-of-scotland-csv-parser/csv-headers)
                                 (spec/gen ::bank-of-scotland-csv-parser/csv-rows)))]
      (later-overlapping-transaction-intervals-take-precedence transaction-intervals))))
