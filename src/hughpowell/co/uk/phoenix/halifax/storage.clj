(ns hughpowell.co.uk.phoenix.halifax.storage
  (:require [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]))

(def ^:private schema
  [{:db/ident       :phoenix/institution
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident       :halifax/date
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident       :halifax/date-entered
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident       :halifax/reference
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :halifax/description
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :halifax/amount
    :db/valueType   :db.type/bigdec
    :db/cardinality :db.cardinality/one}])

(defmethod ig/init-key ::schema [_key _opts]
  schema)

(defmethod ig/init-key ::headers [_key _opts]
  (map :db/ident schema))

(defmethod storage/->db-entity :phoenix/halifax [_institution entity]
  (-> entity
      (update :halifax/date java-time/java-date)
      (update :halifax/date-entered java-time/java-date)))