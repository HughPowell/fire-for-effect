(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher
  (:require [hawk.core :as hawk]
            [integrant.core :as ig]))

(defmethod ig/init-key ::handler [_key {:keys [path filter]}]
  {:watch
   (fn [handler]
     (hawk/watch! [{:paths   [path]
                    :filter  (fn [_ctx {:keys [file]}] (-> filter re-pattern (re-find (.getName file)) boolean))
                    :handler (fn [_ctx {:keys [kind file]}] (handler kind file))}]))
   :stop (fn [watch] (hawk/stop! watch))})
