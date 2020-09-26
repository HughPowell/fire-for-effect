(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer
  (:require [clojure.core.async :as async]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]))

(defn- handle-file-action [headers institution csv-parser date-attribute schemaed-connection completion-channel]
  (fn [action file]
    (try
      (when (= action :modify)
        (let [transactions (map #(zipmap headers (cons institution %)) (csv-parser file))
              dates (map date-attribute transactions)]
          (when (seq transactions)
            (storage/upsert-period-of-transactions
              schemaed-connection
              institution
              date-attribute
              transactions
              (java-time/adjust (apply java-time/min dates) :first-day-of-month)
              (java-time/adjust (apply java-time/max dates) :last-day-of-month))))
        (when (some? completion-channel)
          (async/>!! completion-channel :complete)))
      (catch Throwable t
        (when (some? completion-channel)
          (async/>!! completion-channel t))))))

(defmethod ig/init-key ::importer [_key {:keys                                        [csv-parser schemaed-connection completion-channel]
                                         {:keys [watch] :as file-watcher}             :file-watcher
                                         {:keys [headers institution date-attribute]} :config}]
  (assoc
    file-watcher
    :watch
    (watch (handle-file-action headers institution csv-parser date-attribute schemaed-connection completion-channel))))

(defmethod ig/halt-key! ::importer [_key {:keys [watch stop]}]
  (stop watch))
