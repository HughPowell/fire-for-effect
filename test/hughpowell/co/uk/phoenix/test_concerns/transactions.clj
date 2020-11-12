(ns hughpowell.co.uk.phoenix.test-concerns.transactions
  (:refer-clojure :exclude [sort])
  (:require [clojure.data.csv :as csv]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]))

(defn sort [date-pattern date-index transactions]
  (sort-by (apply juxt
                  (fn [transaction]
                    (java-time/local-date date-pattern (nth transaction date-index)))
                  (take (dec (count (first transactions))) (drop 1 (map #(fn [c] (nth c %)) (range)))))
           transactions))

(defn generator [date-pattern date-index header-spec row-spec]
  (gen/fmap
    (fn [[header rows]] (->> rows (sort date-pattern date-index) (cons header)))
    (gen/tuple
      (spec/gen header-spec)
      (spec/gen row-spec))))


(defn write
  ([input-path filename transactions]
   (write input-path filename "UTF-8" transactions))
  ([input-path filename encoding transactions]
   (with-open [writer (io/writer (fs/file input-path filename) :encoding encoding)]
     (csv/write-csv writer transactions))))
