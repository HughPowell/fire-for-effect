(ns hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser
  (:require [clojure.spec.alpha :as spec]
            [integrant.core :as ig]
            [java-time :as java-time]))

(defn ->money [s]
  (try
    (-> s
        not-empty
        (or 0)
        bigdec
        (.setScale 2))
    (catch Throwable _ ::spec/invalid)))

(spec/def ::date (spec/conformer #(try (java-time/local-date "dd/MM/yyyy" %) (catch Throwable _ ::spec/invalid))))
(spec/def ::type string?)
(spec/def ::sort-code (spec/conformer #(or (re-find #"\d{2}-\d{2}-\d{2}" %) ::spec/invalid)))
(spec/def ::account-number (spec/conformer #(or (re-find #"\d{8}" %) ::spec/invalid)))
(spec/def ::description string?)
(spec/def ::debit (spec/conformer ->money))
(spec/def ::credit (spec/conformer ->money))
(spec/def ::balance (spec/conformer ->money))

(spec/def ::csv-row
  (spec/and
    (spec/tuple ::date ::type ::sort-code ::account-number ::description ::debit ::credit ::balance)
    (fn [[_ _ _ _ _ debit credit _]]
      (or (and (zero? debit) (pos? credit))
          (and (pos? debit) (zero? credit))
          ::spec/invalid))))

(spec/def ::header-date #{"Transaction Date"})
(spec/def ::header-type #{"Transaction Type"})
(spec/def ::header-sort-code #{"Sort Code"})
(spec/def ::header-account-number #{"Account Number"})
(spec/def ::header-description #{"Transaction Description"})
(spec/def ::header-debit #{"Debit Amount"})
(spec/def ::header-credit #{"Credit Amount"})
(spec/def ::header-balance #{"Balance"})

(spec/def ::csv-headers
  (spec/tuple ::header-date ::header-type ::header-sort-code ::header-account-number ::header-description ::header-debit
              ::header-credit ::header-balance))

(defmethod ig/init-key ::spec [_key _opts]
  {:headers-spec ::csv-headers
   :row-spec     ::csv-row})
