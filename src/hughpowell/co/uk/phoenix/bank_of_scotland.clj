(ns hughpowell.co.uk.phoenix.bank-of-scotland
  (:require [duct.core :as duct]
            [integrant.core :as ig]))

(def ^:private config
  {[:duct/const :bank-of-scotland/input-path]         "dev/data/"
   [:duct/const :bank-of-scotland/input-filter]       "BankOfScotland.*\\.csv$"

   :hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser/spec {}
   [:hughpowell.co.uk.phoenix.cross-cutting-concerns.csv-parser/parse :bank-of-scotland/csv-parser]
   (ig/ref :hughpowell.co.uk.phoenix.bank-of-scotland.csv-parser/spec)

   :hughpowell.co.uk.phoenix.bank-of-scotland.storage/schema  {}
   [:hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/schemaed-connection :bank-of-scotland/schemaed-connection]
   {:connection (ig/ref :hughpowell.co.uk.phoenix.cross-cutting-concerns.storage/connection)
    :schema     (ig/ref :hughpowell.co.uk.phoenix.bank-of-scotland.storage/schema)}

   [:hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher/handler :bank-of-scotland/watcher]
   {:path   (ig/ref :bank-of-scotland/input-path)
    :filter (ig/ref :bank-of-scotland/input-filter)}

   :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers {}
   [:hughpowell.co.uk.phoenix.cross-cutting-concerns.transaction-importer/importer :bank-of-scotland/transaction-importer]
   {:file-watcher        (ig/ref :bank-of-scotland/watcher)
    :schemaed-connection (ig/ref :bank-of-scotland/schemaed-connection)
    :csv-parser          (ig/ref :bank-of-scotland/csv-parser)
    :config              {:headers        (ig/ref :hughpowell.co.uk.phoenix.bank-of-scotland.storage/headers)
                          :institution    :phoenix/bank-of-scotland
                          :date-attribute :bank-of-scotland/date}}})

(defmethod ig/init-key ::module [_key opts]
  (fn [base-config]
    (duct/merge-configs
      base-config
      (-> config
          (update [:duct/const :bank-of-scotland/input-path] #(:input-path opts %))
          (update [:duct/const :bank-of-scotland/input-filter] #(:input-filter opts %))))))
