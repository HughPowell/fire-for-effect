(ns hughpowell.co.uk.phoenix.nationwide.storage
  (:require [integrant.core :as ig]
            [java-time :as java-time]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]))

(def ^:private schema
  [{:db/ident       :phoenix/institution
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident       :nationwide/date
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident       :nationwide/type
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :nationwide/description
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :nationwide/paid-out
    :db/valueType   :db.type/bigdec
    :db/cardinality :db.cardinality/one}
   {:db/ident       :nationwide/paid-in
    :db/valueType   :db.type/bigdec
    :db/cardinality :db.cardinality/one}
   {:db/ident       :nationwide/balance
    :db/valueType   :db.type/bigdec
    :db/cardinality :db.cardinality/one}])

(defmethod ig/init-key ::schema [_key _opts]
  schema)

(defmethod ig/init-key ::headers [_key _opts]
  (map :db/ident schema))

(defmethod storage/->db-entity :phoenix/nationwide [_institution entity]
  (update entity :nationwide/date java-time/java-date))