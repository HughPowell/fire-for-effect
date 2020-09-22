(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer
  (:require [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]))

(defmethod ig/init-key ::importer [_key {:keys                                        [file-watcher csv-parser schemaed-connection]
                                         {:keys [headers institution date-attribute]} :config}]
  (file-watcher
    (fn [action file]
      (case action
        :delete nil
        (let [transactions (map #(zipmap headers (cons institution %)) (csv-parser file))
              dates (map date-attribute transactions)]
          (storage/upsert-period-of-transactions
            schemaed-connection
            institution
            date-attribute
            transactions
            (java-time/adjust (apply java-time/min dates) :first-day-of-month)
            (java-time/adjust (apply java-time/max dates) :last-day-of-month)))))))

