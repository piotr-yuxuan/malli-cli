(ns user
  (:require [piotr-yuxuan.malli-config-cli :refer [malli-config-cli]]))

(defn run-x
  [{:keys [arg]}]
  (-> arg
      malli-config-cli
      pr-str
      println))
