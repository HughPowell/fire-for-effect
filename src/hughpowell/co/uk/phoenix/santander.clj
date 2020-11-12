(ns hughpowell.co.uk.phoenix.santander
  (:require [duct.core :as duct]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.csv-parser :as csv-parser]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher :as file-watcher]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer :as transaction-importer]
            [hughpowell.co.uk.phoenix.santander.csv-parser :as santander-csv-parser]
            [hughpowell.co.uk.phoenix.santander.storage :as santander-storage]))

(def ^:private config
  {:santander/input-path   "dev/data/"
   :santander/input-filter "Santander.*\\.csv$"

   ::santander-csv-parser/spec    {}
   [::csv-parser/parse :santander/csv-parser]
   (ig/ref ::santander-csv-parser/spec)

   ::santander-storage/schema     {}
   [::storage/schemaed-connection :santander/schemaed-connection]
   {:connection (ig/ref ::storage/connection)
    :schema     (ig/ref ::santander-storage/schema)}

   [::file-watcher/handler :santander/watcher]
   {:path   (ig/ref :santander/input-path)
    :filter (ig/ref :santander/input-filter)}

   ::santander-storage/headers    {}
   [::transaction-importer/importer :santander/transaction-importer]
   {:schemaed-connection (ig/ref :santander/schemaed-connection)
    :csv-parser          (ig/ref :santander/csv-parser)
    :file-watcher        (ig/ref :santander/watcher)
    :config              {:headers        (ig/ref ::santander-storage/headers)
                          :institution    :phoenix/santander
                          :date-attribute :santander/date}
    :completion-channel  (ig/ref ::transaction-importer/completion-channel)}})

(defmethod ig/init-key ::module [_key _opts]
  (fn [base-config] (duct/merge-configs config base-config)))
