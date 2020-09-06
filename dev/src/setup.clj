(ns setup
  (:gen-class)
  (:require [clj-http.client :as http]))

(defn -main []
  (let [file-names->paths {"local.clj" "dev/src/local.clj"
                           "dir-locals.el" ".dir-locals.el"
                           "local.edn" "dev/resources/local.edn"
                           "profiles.clj" "profiles.clj"}
        url "https://raw.githubusercontent.com/duct-framework/duct/master/lein-duct/resources/leiningen/duct/"]
    (doall
      (map (fn [[file-name path]]
             (->> file-name
                  (str url)
                  http/get
                  :body
                  (spit path)))
           file-names->paths))))
