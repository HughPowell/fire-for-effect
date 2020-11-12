(ns hughpowell.co.uk.phoenix.santander.csv-parser
  (:require [clojure.spec.alpha :as spec]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.units :as units]))

(spec/def ::date (units/date "yyyy-MM-dd"))
(spec/def ::card string?)
(spec/def ::description (spec/and string? seq))
(spec/def ::money-in (units/money "£ "))
(spec/def ::money-out (units/money "£ "))
(spec/def ::empty #{""})

(spec/def ::csv-row
  (spec/and
    (spec/tuple ::empty ::date ::empty ::card ::empty ::description ::empty ::money-in ::empty ::money-out)
    (fn [[_ _ _ _ _ _ _ debit _ credit]]
      (or (and (zero? debit) (pos? credit))
          (and (pos? debit) (zero? credit))))))

(spec/def ::csv-rows (spec/coll-of ::csv-row))

(spec/def ::header-date #{"Date"})
(spec/def ::header-card #{"Card"})
(spec/def ::header-description #{"Description"})
(spec/def ::header-money-in #{"Money in"})
(spec/def ::header-money-out #{"Money Out"})

(spec/def ::csv-headers
  (spec/tuple ::empty ::header-date ::empty ::header-card ::empty ::header-description ::empty ::header-money-in
              ::empty ::header-money-out))

(defmethod ig/init-key ::spec [_key _opts]
  {:headers-spec ::csv-headers
   :rows-spec    ::csv-rows})
