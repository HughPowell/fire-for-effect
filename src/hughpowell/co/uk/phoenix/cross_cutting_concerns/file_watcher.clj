(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher
  (:require [digest :as digest]
            [hawk.core :as hawk]
            [integrant.core :as ig]
            [me.raynes.fs :as fs]))

(defmethod ig/init-key ::handler [_key {:keys [path filter]}]
  (fn [handler]
    (hawk/watch! [{:paths   [path]
                   :filter  (fn [_ctx {:keys [file]}]
                              (-> filter re-pattern (re-find (last (fs/split file))) boolean))
                   :context (constantly {})
                   :handler (fn [context {:keys [file] :as event}]
                              (let [stable-sha-1 (->> (fn []
                                                        (Thread/sleep 100)
                                                        (when (fs/exists? file)
                                                          (digest/sha-1 file)))
                                                      repeatedly
                                                      (partition 2 1)
                                                      (drop-while (fn [[a b]] (not= a b)))
                                                      ffirst)]
                                (when-not (= stable-sha-1 (get context file))
                                  (handler event))
                                (assoc context file stable-sha-1)))}])))