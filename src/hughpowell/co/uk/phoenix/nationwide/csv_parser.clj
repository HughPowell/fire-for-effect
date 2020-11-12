(ns hughpowell.co.uk.phoenix.nationwide.csv-parser
  (:require [clojure.spec.alpha :as spec]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.units :as units]))

(spec/def ::date (units/date "dd MMM yyyy"))
(spec/def ::type (spec/and string? seq))
(spec/def ::description (spec/and string? seq))
(spec/def ::paid-out (units/money "£"))
(spec/def ::paid-in (units/money "£"))
(spec/def ::balance (units/money "£"))

(spec/def ::csv-row
  (spec/and
    (spec/tuple ::date ::type ::description ::paid-out ::paid-in ::balance)
    (fn [[_ _ _ paid-out paid-in _]]
      (or (and (zero? paid-out) (pos? paid-in))
          (and (pos? paid-out) (zero? paid-in))))))

(spec/def ::csv-rows (spec/coll-of ::csv-row))

(spec/def ::header-date #{"Date"})
(spec/def ::header-type #{"Transaction type"})
(spec/def ::header-description #{"Description"})
(spec/def ::header-paid-out #{"Paid out"})
(spec/def ::header-paid-in #{"Paid in"})
(spec/def ::header-balance #{"Balance"})

(spec/def ::csv-headers
  (spec/tuple ::header-date ::header-type ::header-description ::header-paid-out ::header-paid-in ::header-balance))

(defmethod ig/init-key ::spec [_key _opts]
  {:encoding     "ISO-8859-1"
   :headers-spec ::csv-headers
   :rows-spec    ::csv-rows})
