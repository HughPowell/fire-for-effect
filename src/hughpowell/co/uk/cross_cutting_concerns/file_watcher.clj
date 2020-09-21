(ns hughpowell.co.uk.cross-cutting-concerns.file-watcher
  (:require [hawk.core :as hawk]
            [integrant.core :as ig]))

(defmethod ig/init-key ::handler [_key {:keys [path filter handler]}]
  (hawk/watch! [{:paths   [path]
                 :filter  (fn [_ctx {:keys [file]}] (-> filter re-pattern (re-find (.getName file)) boolean))
                 :handler (fn [_ctx {:keys [kind file]}] (handler kind file))}]))

(defmethod ig/halt-key! ::handler [_key watcher]
  (hawk/stop! watcher))
