✔ (ns piotr-yuxuan.malli-cli.utils
?   "General functions and utilities that could be part of clojure standard library, but aren't."
?   (:require [clojure.string :as str]))
  
✔ (def ^{:arglists '([f m])}
?   remove-key
✔   #(#'clojure.core/filter-key first (complement %1) %2))
  
✔ (defn -make-format
?   ;; From clojure/tools.cli
?   "Given a sequence of column widths, return a string suitable for use in
?   format to print a sequences of strings in those columns."
?   [lens]
~   (str/join (map #(str "  %" (when-not (zero? %) (str "-" %)) "s") lens)))
  
✔ (defn deep-merge
?   "It merges maps recursively. It merges the maps from left
?   to right and the right-most value wins. It is useful to merge the
?   user defined configuration on top of the default configuration.
?   example:
?   ``` clojure
?   (deep-merge {:foo 1 :bar {:baz 2}}
?               {:foo 2 :bar {:baz 1 :qux 3}})
?   ;;=> {:foo 2, :bar {:baz 1, :qux 3}}
?   ```
?   From https://github.com/BrunoBonacci/1config"
?   [& maps]
✘   (let [maps (filter (comp not nil?) maps)]
✘     (if (every? map? maps)
✘       (apply merge-with deep-merge maps)
✘       (last maps))))
