(ns dev
  (:refer-clojure :exclude [test])
  (:require [clojure.java.io :as io]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [duct.core :as duct]
            [fipp.edn :refer [pprint]]
            [integrant.repl :refer [clear halt go init prep reset]]
            [integrant.repl.state :refer [config system]]
            [kaocha.repl :as kaocha]))

(duct/load-hierarchy)

(defn read-config []
  (duct/read-config (io/resource "hughpowell/co/uk/phoenix/config.edn")))

(defn test-all []
  (kaocha/run-all))

(defn test [& args]
  (apply kaocha/run args))

(def profiles
  [:duct.profile/dev :ductprofile/test :duct.profile/local])

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")

(when (io/resource "local.clj")
  (load "local"))

(integrant.repl/set-prep! #(duct/prep-config (read-config) profiles))
