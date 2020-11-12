(ns hughpowell.co.uk.phoenix.test-concerns.units)

(defn ->date-string [pattern instant]
  (java-time/format
    pattern
    (-> instant
        java-time/instant
        (java-time/zoned-date-time "Europe/London"))))

(defn ->local-date [re pattern s]
  (->> s
       (re-find re)
       (java-time/local-date pattern)))

(defn ->money-string
  ([d] (->money-string "" d))
  ([sym d] (if (zero? d) "" (str sym d))))
