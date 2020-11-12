(ns hughpowell.co.uk.phoenix.nationwide
  (:require [duct.core :as duct]
            [integrant.core :as ig]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.csv-parser :as csv-parser]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher :as file-watcher]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.storage :as storage]
            [hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer :as transaction-importer]
            [hughpowell.co.uk.phoenix.nationwide.csv-parser :as nationwide-csv-parser]
            [hughpowell.co.uk.phoenix.nationwide.storage :as nationwide-storage]))

(def ^:private config
  {:nationwide/input-path   "dev/data/"
   :nationwide/input-filter "Nationwide.*\\.csv$"

   ::nationwide-csv-parser/spec   {}
   [::csv-parser/parse :nationwide/csv-parser] (ig/ref ::nationwide-csv-parser/spec)

   ::nationwide-storage/schema    {}
   [::storage/schemaed-connection :nationwide/schemaed-connection]
   {:connection (ig/ref ::storage/connection)
    :schema     (ig/ref ::nationwide-storage/schema)}

   [::file-watcher/handler :nationwide/watcher]
   {:path   (ig/ref :nationwide/input-path)
    :filter (ig/ref :nationwide/input-filter)}

   ::nationwide-storage/headers   {}
   [::transaction-importer/importer :nationwide/transaction-importer]
   {:schemaed-connection (ig/ref :nationwide/schemaed-connection)
    :csv-parser          (ig/ref :nationwide/csv-parser)
    :file-watcher        (ig/ref :nationwide/watcher)
    :config              {:headers        (ig/ref ::nationwide-storage/headers)
                          :institution    :phoenix/nationwide
                          :date-attribute :nationwide/date}
    :completion-channel  (ig/ref ::transaction-importer/completion-channel)}})

(defmethod ig/init-key ::module [_key _opts]
  (fn [base-config] (duct/merge-configs config base-config)))
