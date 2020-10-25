(ns hughpowell.co.uk.phoenix.bank-of-scotland
  (:require [duct.core :as duct]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer :as bank-of-scotland-transaction-importer]))

(def ^:private config
  {:bank-of-scotland/input-path         "dev/data/"
   :bank-of-scotland/input-filter       "BankOfScotland.*\\.csv$"

   :hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser/spec {}
   [:hughpowell.co.uk.phoenix.cross-cutting-concerns.csv-parser/parse :bank-of-scotland/csv-parser]
   (ig/ref :hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser/spec)

   :hughpowell.co.uk.phoenix.bank-of-scotland.storage/schema  {}
   [:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/schemaed-connection :bank-of-scotland/schemaed-connection]
   {:connection (ig/ref :hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection)
    :schema     (ig/ref :hughpowell.co.uk.phoenix.bank-of-scotland.storage/schema)}

   [:hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher/handler :bank-of-scotland/watcher]
   {:path    (ig/ref :bank-of-scotland/input-path)
    :filter  (ig/ref :bank-of-scotland/input-filter)}

   :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers {}
   [:hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/importer :bank-of-scotland/transaction-importer]
   {:schemaed-connection (ig/ref :bank-of-scotland/schemaed-connection)
    :csv-parser          (ig/ref :bank-of-scotland/csv-parser)
    :file-watcher        (ig/ref :bank-of-scotland/watcher)
    :config              {:headers        (ig/ref :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers)
                          :institution    :phoenix/bank-of-scotland
                          :date-attribute :bank-of-scotland/date}
    :completion-channel (ig/ref ::bank-of-scotland-transaction-importer/completion-channel)}})

(defmethod ig/init-key ::module [_key _opts]
  (fn [base-config]
    (duct/merge-configs
      base-config
      (-> config
          (update :bank-of-scotland/input-path #(:bank-of-scotland/input-path base-config %))
          (update :bank-of-scotland/input-filter #(:bank-of-scotland/input-filter base-config %))
          (update ::bank-of-scotland-transaction-importer/completion-channel
                  #(::bank-of-scotland-transaction-importer/completion-channel base-config %))))))
