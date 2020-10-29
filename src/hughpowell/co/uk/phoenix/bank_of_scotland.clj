(ns hughpowell.co.uk.phoenix.bank-of-scotland
  (:require [duct.core :as duct]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.csv-parser :as csv-parser]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher :as file-watcher]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer :as transaction-importer]
            [hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser :as bank-of-scotland-csv-parser]
            [hughpowell.co.uk.phoenix.bank-of-scotland.storage :as bank-of-scotland-storage]))

(def ^:private config
  {:bank-of-scotland/input-path       "dev/data/"
   :bank-of-scotland/input-filter     "BankOfScotland.*\\.csv$"

   ::bank-of-scotland-csv-parser/spec {}
   [::csv-parser/parse :bank-of-scotland/csv-parser]
   (ig/ref ::bank-of-scotland-csv-parser/spec)

   ::bank-of-scotland-storage/schema  {}
   [::storage/schemaed-connection :bank-of-scotland/schemaed-connection]
   {:connection (ig/ref ::storage/connection)
    :schema     (ig/ref ::bank-of-scotland-storage/schema)}

   [::file-watcher/handler :bank-of-scotland/watcher]
   {:path   (ig/ref :bank-of-scotland/input-path)
    :filter (ig/ref :bank-of-scotland/input-filter)}

   ::bank-of-scotland-storage/headers  {}
   [::transaction-importer/importer :bank-of-scotland/transaction-importer]
   {:schemaed-connection (ig/ref :bank-of-scotland/schemaed-connection)
    :csv-parser          (ig/ref :bank-of-scotland/csv-parser)
    :file-watcher        (ig/ref :bank-of-scotland/watcher)
    :config              {:headers        (ig/ref ::bank-of-scotland-storage/headers)
                          :institution    :phoenix/bank-of-scotland
                          :date-attribute :bank-of-scotland/date}
    :completion-channel  (ig/ref ::transaction-importer/completion-channel)}})

(defmethod ig/init-key ::module [_key _opts]
  (fn [base-config] (duct/merge-configs config base-config)))
