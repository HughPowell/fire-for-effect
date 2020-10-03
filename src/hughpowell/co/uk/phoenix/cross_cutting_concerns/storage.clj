(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.storage
  (:require [clojure.data :as data]
            [clojure.set :as set]
            [datahike.api :as datahike]
            [integrant.core :as ig]
            [java-time :as java-time])
  (:import (clojure.lang ExceptionInfo)))

(defn upsert-interval-of-transactions [connection institution date-attribute new-transactions interval]
  (let [current-transactions (datahike/q
                               '[:find [(pull ?e [*]) ...]
                                 :in $ ?institution ?date-attribute ?interval
                                 :where [?e :phoenix/institution ?institution]
                                 [?e ?date-attribute ?date]
                                 [(java-time/contains? ?interval ?date)]]
                               @connection
                               institution
                               date-attribute
                               interval)
        [additions retractions] (->> new-transactions
                                     (map #(update % date-attribute java-time/java-date))
                                     (reduce
                                       (fn [[additions retractions] item]
                                         (let [[n m] (split-with #(not= (dissoc % :db/id) item) retractions)]
                                           (if (seq m)
                                             [additions (concat n (rest m))]
                                             [(conj additions item) retractions])))
                                       [[] current-transactions]))]
    (when (seq additions)
      (datahike/transact connection additions))
    (when (seq retractions)
      (datahike/transact connection (mapv #(vector :db/retractEntity %) (map :db/id retractions))))))

(defn- ensure-schema [connection schema]
  (let [[existing-schema new-schema] (data/diff
                                       (set (map
                                              #(dissoc % :db/id)
                                              (datahike/q
                                                '[:find [(pull ?e [*]) ...]
                                                  :where [?e :db/ident]]
                                                @connection)))
                                       (set schema))
        modified-schema (into {} (map (fn [ident]
                                        [(get existing-schema ident)
                                         (get new-schema ident)])
                                      (set/intersection (set (map :db/ident existing-schema)) (set (map :db/ident new-schema)))))]
    (when-not (empty? modified-schema)
      (throw (ex-info "Schema has been modified." modified-schema)))
    (when-not (empty? new-schema)
      (datahike/transact connection (vec new-schema)))))

(defmethod ig/init-key ::schemaed-connection [_key {:keys [connection schema]}]
  (ensure-schema connection schema)
  connection)

(defmethod ig/init-key ::connection [_key {:keys [storage]}]
  (let [config {:storage storage}]
    (try
      (datahike/connect config)
      (catch ExceptionInfo ex
        (if (= (:type (ex-data ex) :backend-does-not-exist))
          (do
            (datahike/create-database config)
            (datahike/connect config))
          (throw ex))))))

(defmethod ig/halt-key! ::connection [_key connection]
  (datahike/release connection))
