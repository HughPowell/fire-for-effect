(ns hughpowell.co.uk.phoenix.cross-cutting-concerns.file-watcher
  (:require [clojure.core.async :as async]
            [digest :as digest]
            [hawk.core :as hawk]
            [integrant.core :as ig]
            [me.raynes.fs :as fs]))

(defmethod ig/init-key ::handler [_key {:keys [path filter channel]}]
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
                                (async/>!! channel event))
                              (assoc context file stable-sha-1)))}]))

(defmethod ig/halt-key! ::handler [_key watch]
  (hawk/stop! watch))

(defmethod ig/init-key ::channel [_key {:keys [size]}]
  (async/chan size))

(defmethod ig/halt-key! ::channel [_key channel]
  (async/close! channel))