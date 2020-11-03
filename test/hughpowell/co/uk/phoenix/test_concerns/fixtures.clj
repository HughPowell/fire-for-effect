(ns hughpowell.co.uk.phoenix.test-concerns.fixtures
  (:require [clojure.core.async :as async]
            [datahike.api :as datahike]
            [duct.core :as duct]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer :as transaction-importer]))

(duct/load-hierarchy)

(defmethod ig/init-key ::transaction-importer/completion-channel [_key _opts]
  (async/chan 10))

(defn complete [channel]
  (let [one-second (* 1 1000)]
    (let [[result _channel] (async/alts!! [channel (async/timeout one-second)])]
      (when (instance? Throwable result)
        (throw result)))))

(defmacro with-resource [binding close-fn & body]
  `(let ~binding
     (try
       (do ~@body)
       (finally
         (~close-fn ~(binding 0))))))

(defmacro with-sut [sym & body]
  `(let [config# (-> (duct/resource "hughpowell/co/uk/phoenix/config.edn")
                     (duct/read-config)
                     (duct/prep-config [:duct.profile/test]))
         clean-up# (fn [sut#]
                     (ig/halt! sut#)
                     (datahike/delete-database (::storage/connection config#)))]
     (with-resource [~sym (ig/init config#)] clean-up# ~@body)))
