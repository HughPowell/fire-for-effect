(ns hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.units :as units]))

(spec/def ::date units/date)
(spec/def ::type (spec/and string? seq))
(spec/def ::sort-code (spec/with-gen
                        (spec/conformer #(or (re-find #"\d{2}-\d{2}-\d{2}" %) ::spec/invalid))
                        #(gen/fmap (fn [[a b c]] (format "%s-%s-%s" a b c))
                                   (apply gen/tuple (repeat 3 (spec/gen (spec/int-in 0 100)))))))
(spec/def ::account-number (spec/with-gen
                             (spec/conformer #(or (re-find #"\d{8}" %) ::spec/invalid))
                             #(gen/fmap (fn [n] (format "%08d" n))
                                        (spec/gen (spec/int-in 0 100000000)))))
(spec/def ::description (spec/and string? seq))
(spec/def ::debit units/money)
(spec/def ::credit units/money)
(spec/def ::balance units/money)

(spec/def ::csv-row
  (spec/and
    (spec/tuple ::date ::type ::sort-code ::account-number ::description ::debit ::credit ::balance)
    (fn [[_ _ _ _ _ debit credit _]]
      (or (and (zero? debit) (pos? credit))
          (and (pos? debit) (zero? credit))))))

(spec/def ::csv-rows (spec/coll-of ::csv-row))

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
   :rows-spec    ::csv-rows})
