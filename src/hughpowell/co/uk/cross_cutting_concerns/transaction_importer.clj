(ns hughpowell.co.uk.cross-cutting-concerns.transaction-importer
  (:require [integrant.core :as ig]))

(defmethod ig/init-key ::importer [_key {:keys [csv-parser upsert headers institution date-attribute]}]
  (fn [action file]
    (case action
      :delete nil
      (let [transactions (map #(zipmap headers (cons institution %)) (csv-parser file))
            dates (map date-attribute transactions)]
        (upsert
          institution
          date-attribute
          transactions
          (java-time/adjust (apply java-time/min dates) :first-day-of-month)
          (java-time/adjust (apply java-time/max dates) :last-day-of-month))))))

