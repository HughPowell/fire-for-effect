(ns hughpowell.co.uk.phoenix.test-concerns.transactions
  (:refer-clojure :exclude [sort])
  (:require [clojure.data.csv :as csv]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]))

(defn sort [date-pattern transactions]
  (sort-by (apply juxt
                  (fn [[date]]
                    (java-time/local-date date-pattern date))
                  (take (dec (count (first transactions))) (drop 1 (map #(fn [c] (nth c %)) (range)))))
           transactions))

(defn generator [date-pattern header-spec row-spec]
  (gen/fmap
    (fn [[header rows]] (->> rows (sort date-pattern) (cons header)))
    (gen/tuple
      (spec/gen header-spec)
      (spec/gen row-spec))))


(defn write
  ([input-path filename transactions]
   (write input-path filename "UTF-8" transactions))
  ([input-path filename encoding transactions]
   (with-open [writer (io/writer (fs/file input-path filename) :encoding encoding)]
     (csv/write-csv writer transactions))))
