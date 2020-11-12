(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.units
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as string]
            [java-time :as java-time])
  (:import (java.math RoundingMode)))

(defn money
  ([] (money ""))
  ([sym]
   (spec/with-gen
     (spec/conformer
       (fn [s]
         (try
           (-> s
               (string/replace-first (re-pattern sym) "")
               not-empty
               (or 0)
               bigdec
               (.setScale 2))
           (catch Throwable _ ::spec/invalid))))
     #(gen/fmap (fn [amount]
                  (let [rounded-amount (.setScale (bigdec amount) 2 RoundingMode/HALF_UP)]
                    (if (zero? rounded-amount)
                      ""
                      (str sym rounded-amount))))
                (spec/gen (spec/double-in :infinite? false :NaN? false :min 0.00))))))

(defn date [pattern]
  (spec/with-gen
    (spec/conformer #(try
                       (java-time/zoned-date-time
                         (java-time/local-date pattern %)
                         (java-time/local-time 0)
                         "Europe/London")
                       (catch Throwable _ ::spec/invalid)))
    #(gen/fmap (fn [date] (->> date
                               (apply java-time/local-date)
                               (java-time/format pattern)))
               (gen/such-that
                 (fn [date] (try (apply java-time/local-date date) (catch Throwable _ false)))
                 (gen/tuple
                   (spec/gen (spec/int-in 2000 2100))
                   (spec/gen (spec/int-in 1 13))
                   (spec/gen (spec/int-in 1 32)))))))
