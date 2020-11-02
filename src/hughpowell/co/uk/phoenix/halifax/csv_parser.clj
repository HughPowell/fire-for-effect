(ns hughpowell.co.uk.phoenix.halifax.csv-parser
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.units :as units]))

(spec/def ::date units/date)
(spec/def ::date-entered units/date)
(spec/def ::reference string?)
(spec/def ::description (spec/and
                          string?
                          (spec/conformer string/trimr)))
(spec/def ::amount units/money)
(spec/def ::empty #{""})

(spec/def ::csv-row (spec/tuple ::date ::date-entered ::reference ::description ::amount ::empty))

(spec/def ::csv-rows (spec/coll-of ::csv-row :kind sequential?))

(spec/def ::header-date #{"Date"})
(spec/def ::header-date-entered #{"Date entered"})
(spec/def ::header-reference #{"Reference"})
(spec/def ::header-description #{"Description"})
(spec/def ::header-amount #{"Amount"})

(spec/def ::csv-headers
  (spec/tuple ::header-date ::header-date-entered ::header-reference ::header-description ::header-amount ::empty))

(defmethod ig/init-key ::spec [_key _opts]
  {:headers-spec ::csv-headers
   :rows-spec    ::csv-rows})