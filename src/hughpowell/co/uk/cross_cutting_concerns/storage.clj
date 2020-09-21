(ns hughpowell.co.uk.cross-cutting-concerns.storage
  (:require [clojure.data :as data]
            [clojure.set :as set]
            [datahike.api :as datahike]
            [integrant.core :as ig])
  (:import (clojure.lang ExceptionInfo)
           (java.time ZoneId)))

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

(defn- ->local-date [instant]
  (-> instant
      java-time/instant
      (java-time/zoned-date-time (ZoneId/systemDefault))
      java-time/local-date))

(defn in-date-range? [start-date date end-date]
  (let [local-date (->local-date date)]
    (and (not (java-time/before? local-date start-date))
         (not (java-time/after? local-date end-date)))))

(defn- ->instant [local-date]
  (java-time/java-date (apply java-time/offset-date-time (java-time/as local-date :year :month-of-year :day-of-month))))

(defmethod ig/init-key ::upsert-period-of-transactions [_key {:keys [connection schema]}]
  (ensure-schema connection schema)
  (fn [institution date-attribute new-transactions start-date end-date]
    (let [current-transactions (datahike/q
                                 '[:find [(pull ?e [*]) ...]
                                   :in $ ?institution ?date-attribute ?start-date ?end-date
                                   :where [?e :fire-for-effect/institution ?institution]
                                   [?e ?date-attribute ?date]
                                   [(hughpowell.co.uk.cross-cutting-concerns.storage/in-date-range? ?start-date ?date ?end-date)]]
                                 @connection
                                 institution
                                 date-attribute
                                 start-date
                                 end-date)
          [additions retractions] (->> new-transactions
                                       (map #(update % date-attribute ->instant))
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
        (datahike/transact connection (mapv #(vector :db/retractEntity %) (map :db/id retractions)))))))

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
