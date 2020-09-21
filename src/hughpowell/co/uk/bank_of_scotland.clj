(ns hughpowell.co.uk.bank-of-scotland
  (:require [clojure.spec.alpha :as spec]
            [java-time :as java-time]
            [integrant.core :as ig]))

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

(defmethod ig/init-key ::row-spec [_key _opts] ::csv-row)

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

(defmethod ig/init-key ::header-spec [_key _opts] ::csv-headers)

(def schema
  [{:db/ident       :fire-for-effect/institution
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident       :bank-of-scotland/date
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident       :bank-of-scotland/type
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :bank-of-scotland/sort-code
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :bank-of-scotland/account-number
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :bank-of-scotland/description
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :bank-of-scotland/debit
    :db/valueType   :db.type/bigdec
    :db/cardinality :db.cardinality/one}
   {:db/ident       :bank-of-scotland/credit
    :db/valueType   :db.type/bigdec
    :db/cardinality :db.cardinality/one}
   {:db/ident       :bank-of-scotland/balance
    :db/valueType   :db.type/bigdec
    :db/cardinality :db.cardinality/one}])

(defmethod ig/init-key ::schema [_key _opts] schema)

(defmethod ig/init-key ::headers [_key _opts]
  (map :db/ident schema))