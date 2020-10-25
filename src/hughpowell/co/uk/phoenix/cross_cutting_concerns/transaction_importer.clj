(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer
  (:require [clojure.core.async :as async]
            [hawk.core :as hawk]
            [integrant.core :as ig]
            [java-time :as java-time]
            [me.raynes.fs :as fs]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]))

(defn- handle-file-action [headers institution csv-parser date-attribute schemaed-connection completion-channel]
  (fn [{:keys [kind file]}]
    (try
      (when (and (#{:create :modify} kind)
                 (not (zero? (fs/size file))))
        (let [transactions (map #(zipmap headers (cons institution %)) (csv-parser file))
              dates (map date-attribute transactions)]
          (when (seq transactions)
            (storage/upsert-interval-of-transactions
              schemaed-connection
              institution
              date-attribute
              transactions
              (java-time/interval
                (apply java-time/min dates)
                (-> (apply java-time/max dates)
                    (java-time/plus (java-time/days 1)))))))
        (when (some? completion-channel)
          (async/>!! completion-channel :complete)))
      (catch Throwable t
        (when (some? completion-channel)
          (async/>!! completion-channel t))))))

(defmethod ig/init-key ::importer [_key {:keys                                        [file-watcher csv-parser schemaed-connection completion-channel]
                                         {:keys [headers institution date-attribute]} :config}]
  (file-watcher (handle-file-action headers institution csv-parser date-attribute schemaed-connection completion-channel)))

(defmethod ig/halt-key! ::importer [_key watch]
  (hawk/stop! watch))