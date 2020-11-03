(ns hughpowell.co.uk.phoenix.test-concerns.dates)

(defn ->date-string [instant]
  (java-time/format
    "dd/MM/yyyy"
    (-> instant
        java-time/instant
        (java-time/zoned-date-time "Europe/London"))))

(defn ->local-date [s]
  (->> s
       (re-find #"(\d{2})\/(\d{2})\/(\d{4})")
       rest
       (map #(Integer/parseInt %))
       reverse
       (apply java-time/local-date)))
