(ns hughpowell.co.uk.cross-cutting-concerns.csv-parser
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [clojure.spec.alpha :as spec]))

(defn- conform [spec x]
  (let [conformed (spec/conform spec x)]
    (if (not= conformed ::spec/invalid)
      conformed
      (throw (ex-info (spec/explain spec x) (spec/explain-data spec x))))))

(defn- validate [spec x]
  (or (spec/valid? spec x)
      (throw (ex-info (spec/explain spec x) (spec/explain-data spec x)))))

(defn- validate-&-strip-headers [header-spec [headers & rows]]
  (validate header-spec headers)
  rows)

(defn- parse-csv [file header-spec row-spec]
  (with-open [reader (io/reader file)]
    (->> reader
         csv/read-csv
         (validate-&-strip-headers header-spec)
         (map #(conform row-spec %))
         doall)))

(defmethod ig/init-key ::parse [_key {:keys [header-spec row-spec]}]
  (fn [f] (parse-csv f header-spec row-spec)))