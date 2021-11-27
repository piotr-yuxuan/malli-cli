(ns piotr-yuxuan.malli
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
  "Copied from `malli.transform/default-value-transformer`, doesn't
  change existing behaviour but only add `default-fn` optional
  argument. When set, this function is applied on the default value
  found under key `key`."
  ([]
   (default-value-transformer nil))
  ([{:keys [key default-fn defaults] :or {key :default, default-fn identity}}]
   (let [get-default (fn [schema]
                       (if-some [default (some-> schema m/properties key)]
                         default
                         (some->> schema m/type (get defaults) (#(% schema)))))
         set-default {:compile (fn [schema _]
                                 (when-some [default (get-default schema)]
                                   (fn [x] (if (nil? x) default x))))}
         add-defaults {:compile (fn [schema _]
                                  (let [defaults (into {}
                                                       (keep (fn [[k {default key :keys [optional]} v]]
                                                               (when-not optional
                                                                 (when-some [default (if (some? default)
                                                                                       default
                                                                                       (get-default v))]
                                                                   [k default]))))
                                                       (m/children schema))]
                                    (when (seq defaults)
                                      (fn [x]
                                        (if (map? x)
                                          (reduce-kv
                                            (fn [acc k v]
                                              (if-not (contains? x k)
                                                (if-let [d (default-fn v)]
                                                  (assoc acc k d)
                                                  acc)
                                                acc))
                                            x defaults)
                                          x)))))}]
     (mt/transformer
       {:default-decoder set-default
        :default-encoder set-default}
       {:decoders {:map add-defaults}
        :encoders {:map add-defaults}}))))
