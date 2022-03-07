(ns piotr-yuxuan.malli-cli.malli
  "Functions or overloaded functions that could be part of malli, but aren't (as of now)."
  (:require [malli.core :as m]
            [malli.transform :as mt]
            [malli.util :as mu]))

(defn value-schemas
  "Returns all leaf sub schemas for unique paths as a vector of maps
  with :schema, :path and :in keys."
  [schema]
  (->> schema
       mu/subschemas
       (remove (comp #{:map} m/type :schema))))

(defn default-value-transformer
  "Copied from [[malli.transform/default-value-transformer]], doesn't
  change existing behaviour but only add `default-fn` optional
  argument. When set, this function takes one argument: the default
  value found under key `key` (which defaults to `:default`). It is
  called at transformer run time, not compile time."
  ([]
   (default-value-transformer nil))
  ([{:keys [key default-fn defaults] :or {key :default, default-fn identity}}]
   (let [get-default (fn [schema]
                       (if-some [e (some-> schema m/properties (find key))]
                         (constantly (val e))
                         (some->> schema m/type (get defaults) (#(constantly (% schema))))))
         set-default {:compile (fn [schema _]
                                 (when-some [f (get-default schema)]
                                   (fn [x] (if (nil? x) (default-fn (f)) x))))}
         add-defaults {:compile (fn [schema _]
                                  (let [defaults (into {}
                                                       (keep (fn [[k {:keys [optional] :as p} v]]
                                                               (when-not optional
                                                                 (let [e (find p key)]
                                                                   (when-some [f (if e
                                                                                   (constantly (val e))
                                                                                   (get-default v))]
                                                                     [k (comp default-fn f)])))))
                                                       (m/children schema))]
                                    (when (seq defaults)
                                      (fn [x]
                                        (if (map? x)
                                          (reduce-kv
                                            (fn [acc k f]
                                              (if-not (contains? x k)
                                                (assoc acc k (f))
                                                acc))
                                            x defaults)
                                          x)))))}]
     (mt/transformer
       {:default-decoder set-default
        :default-encoder set-default}
       {:decoders {:map add-defaults}
        :encoders {:map add-defaults}}))))
