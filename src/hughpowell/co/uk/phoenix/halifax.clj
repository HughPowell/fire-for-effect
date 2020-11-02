(ns hughpowell.co.uk.phoenix.halifax
  (:require [duct.core :as duct]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.csv-parser :as csv-parser]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher :as file-watcher]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer :as transaction-importer]
            [hughpowell.co.uk.phoenix.halifax.csv-parser :as halifax-csv-parser]
            [hughpowell.co.uk.phoenix.halifax.storage :as halifax-storage]))

(def ^:private config
  {:halifax/input-path       "dev/data/"
   :halifax/input-filter     "Halifax.*\\.csv$"

   ::halifax-csv-parser/spec {}
   [::csv-parser/parse :halifax/csv-parser]
   (ig/ref ::halifax-csv-parser/spec)

   ::halifax-storage/schema  {}
   [::storage/schemaed-connection :halifax/schemaed-connection]
   {:connection (ig/ref ::storage/connection)
    :schema     (ig/ref ::halifax-storage/schema)}

   [::file-watcher/handler :halifax/watcher]
   {:path   (ig/ref :halifax/input-path)
    :filter (ig/ref :halifax/input-filter)}

   ::halifax-storage/headers  {}
   [::transaction-importer/importer :halifax/transaction-importer]
   {:schemaed-connection (ig/ref :halifax/schemaed-connection)
    :csv-parser          (ig/ref :halifax/csv-parser)
    :file-watcher        (ig/ref :halifax/watcher)
    :config              {:headers        (ig/ref ::halifax-storage/headers)
                          :institution    :phoenix/halifax
                          :date-attribute :halifax/date}
    :completion-channel  (ig/ref ::transaction-importer/completion-channel)}})

(defmethod ig/init-key ::module [_key _opts]
  (fn [base-config] (duct/merge-configs config base-config)))
