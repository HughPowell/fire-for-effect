(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.units
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen])
  (:import (java.math RoundingMode)
           (java.text SimpleDateFormat)))

(def money
  (spec/with-gen
    (spec/conformer
      (fn [s]
        (try
          (-> s not-empty (or 0) bigdec (.setScale 2))
          (catch Throwable _ ::spec/invalid))))
    #(gen/fmap (fn [d] (str (.setScale (bigdec d) 2 RoundingMode/HALF_UP)))
               (spec/gen (spec/double-in :infinite? false :NaN? false :min 0.00)))))

(def date
  (spec/with-gen
    (spec/conformer #(try
                       (java-time/zoned-date-time
                         (java-time/local-date "dd/MM/yyyy" %)
                         (java-time/local-time 0)
                         "Europe/London")
                       (catch Throwable _ ::spec/invalid)))
    #(gen/fmap (fn [d] (.format (SimpleDateFormat. "dd/MM/yyy") d))
               (spec/gen (spec/inst-in #inst "2000" #inst "2100")))))