(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer
  (:require [clojure.core.async :as async]
            [integrant.core :as ig]
            [java-time :as java-time]
            [me.raynes.fs :as fs]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]))

(defn- handle-file-action [headers institution csv-parser date-attribute schemaed-connection completion-channel]
  (fn [action file]
    (try
      (when (and (#{:create :modify} action)
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
                (java-time/adjust (apply java-time/min dates) :first-day-of-month)
                (-> (apply java-time/max dates)
                    (java-time/adjust :first-day-of-month)
                    (java-time/plus (java-time/months 1))
                    (java-time/minus (java-time/millis 1)))))))
        (when (some? completion-channel)
          (async/>!! completion-channel :complete)))
      (catch Throwable t
        (when (some? completion-channel)
          (async/>!! completion-channel t))))))

(defmethod ig/init-key ::importer [_key {:keys                                        [csv-parser schemaed-connection input-channel completion-channel]
                                         {:keys [headers institution date-attribute]} :config}]
  (async/go-loop [handler (handle-file-action headers institution csv-parser date-attribute schemaed-connection completion-channel)]
    (when-let [event (async/<! input-channel)]
      (let [{:keys [kind file]} event]
        (handler kind file)
        (recur handler)))))
