(ns hughpowell.co.uk.phoenix.bank-of-scotland.storage
  (:require [integrant.core :as ig]))

(def ^:private schema
  [{:db/ident       :phoenix/institution
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

(defmethod ig/init-key ::schema [_key _opts]
  schema)

(defmethod ig/init-key ::headers [_key _opts]
  (map :db/ident schema))
