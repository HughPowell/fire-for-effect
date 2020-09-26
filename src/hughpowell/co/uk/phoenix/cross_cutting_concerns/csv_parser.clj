(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.csv-parser
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [integrant.core :as ig]))

(defn- throw-spec-failure [spec x]
  (throw (ex-info (spec/explain spec x) (spec/explain-data spec x))))

(defn- conform [spec x]
  (let [conformed (spec/conform spec x)]
    (if (not= conformed ::spec/invalid)
      conformed
      (throw-spec-failure spec x))))

(defn- validate [spec x]
  (or (spec/valid? spec x)
      (throw-spec-failure spec x)))

(defn- validate-&-strip-headers [headers-spec [headers & rows]]
  (validate headers-spec headers)
  (or rows []))

(defn- parse-csv [file headers-spec rows-spec]
  (with-open [reader (io/reader file)]
    (->> reader
         csv/read-csv
         (validate-&-strip-headers headers-spec)
         (conform rows-spec)
         doall)))

(defmethod ig/init-key ::parse [_key {:keys [headers-spec rows-spec]}]
  (fn [f] (parse-csv f headers-spec rows-spec)))