(ns piotr-yuxuan.malli-cli
  (:require [clojure.data]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.transform :as mt]
            [malli.util :as mu])
  (:import (clojure.lang MapEntry)))

(defn children-successor
  "Given a schema `[:enum :a :b :c :d]`, return a Clojure map that
  return the next item of :a -> :b -> :c -> :d <-> :d. The last item
  is mapped onto itself. If the schema has a default value as a
  property, like in `[:enum {:default :b} :a :b :c :d]` an additional
  mapping will be made nil -> :c.

  Primarily intended to be used on enum schema, but code is generic so
  you might think of a use case on another schema type."
  [schema]
  (let [properties (m/properties schema)
        children (m/children schema)
        last-child (last children)]
    (cond-> {}
      :item->successor (into (map vec (partition 2 1 children)))
      ;; The transformer `mt/default-value-transformer` is only
      ;; applied once the args vector is decoded into a map, so we
      ;; have to handle it manually.
      (contains? properties :default) (as-> $ (assoc $ nil (get $ (:default properties))))
      :loop-on-last (assoc last-child last-child))))

(defn name-items
  "Take an argument and return a vector of items that will form an
  option name. For example the option name for `:a/b` will be a-b."
  [x]
  (cond (not (keyword? x)) [(str x)]
        (namespace x) [(namespace x) (name x)]
        :else [(name x)]))

(defn value-schemas
  "Returns all leaf sub schemas for unique paths as a vector of maps
  with :schema, :path and :in keys."
  [schema]
  (->> schema
       mu/subschemas
       (remove (comp #{:map} m/type :schema))))

(def ^{:arglists '([f m])}
  remove-key
  #(#'clojure.core/filter-key first (complement %1) %2))

(def default-arg-number
  1)

(defn ^MapEntry long-option->value-schema
  [default-label value-schema]
  (when-let [long-option (get value-schema
                              :long-option
                              (when (< 1 (count default-label))
                                (str "--" default-label)))]
    (MapEntry. long-option
               (-> (remove-key (comp #{"long-option" "short-option"} namespace) value-schema)
                   (assoc :arg-number (or (:long-option/arg-number value-schema)
                                          (:arg-number value-schema)
                                          default-arg-number))
                   (assoc :update-fn (or (:long-option/update-fn value-schema)
                                         (:update-fn value-schema)))))))

(defn ^MapEntry short-option->value-schema
  [default-label value-schema]
  (when-let [short-option (get value-schema
                               :short-option
                               (when (= 1 (count default-label))
                                 (str "-" default-label)))]
    (MapEntry. short-option
               (-> (remove-key (comp #{"long-option" "short-option"} namespace) value-schema)
                   (assoc :arg-number (or (:short-option/arg-number value-schema)
                                          (:arg-number value-schema)
                                          default-arg-number))
                   (assoc :update-fn (or (:short-option/update-fn value-schema)
                                         (:update-fn value-schema)))))))

(defn label+value-schema
  "Return `MapEntry` items, when applicable one for short, and long
  option names."
  [{:keys [in schema] :as value-schema}]
  (let [in' (remove #(and (keyword? %) (= (namespace %) "malli.core")) in)
        default-label (->> in' (mapcat name-items) (str/join "-"))
        value-schema' (-> value-schema
                          (merge (m/type-properties schema) (m/properties schema))
                          (assoc :in in'))]
    [(long-option->value-schema default-label value-schema')
     (short-option->value-schema default-label value-schema')]))

(defrecord ParsingResult
  [options argstail])

(defn ^ParsingResult -parse-option
  "Take the current arglist head `arg`, the tail args-tail`. Depending
  on the value schema consume some items from the tail and when
  applicable pass them on to `update-fn`. This is actually the core of
  the work that transforms a vector of string to a map of options."
  [{:keys [in update-fn arg-number schema] :as value-schema} options arg argstail]
  (cond (and update-fn (not arg-number)) (ParsingResult. (update options ::schema-errors conj {:message "update-fn needs arg-number", :arg arg, :schema schema})
                                                         argstail)
        (and update-fn arg-number) (ParsingResult. (update-fn options value-schema (take arg-number argstail))
                                                   (drop arg-number argstail))
        (= 0 arg-number) (ParsingResult. (assoc-in options in true)
                                         argstail)
        (= 1 arg-number) (ParsingResult. (assoc-in options in (first argstail))
                                         (rest argstail))
        (number? arg-number) (ParsingResult. (assoc-in options in (vec (take arg-number argstail)))
                                             (drop arg-number argstail))
        :generic-error (ParsingResult. (update options ::schema-errors conj {:message "generic error", :arg arg, :schema schema})
                                       argstail)))

(defn break-short-option-group
  "Expand a group of short option labels into a several short labels and
  interpolate them with the tail of the arglist args-tail` depending
  on the number of arguments each option needs. "
  [label+value-schemas arg argstail]
  (loop [[{:keys [arg-number short-option] :as value-schema} & ss] (->> (rest arg)
                                                                        (map #(str "-" %))
                                                                        (map label+value-schemas))
         interpolated-args ()
         argstail argstail]
    (if (nil? value-schema)
      (into argstail interpolated-args)
      (recur ss
             (into (cons short-option interpolated-args) (take arg-number argstail))
             (drop arg-number argstail)))))

(defn break-long-option-and-value
  "Expand an argument that contains both an option label and a value
  into two arguments: the label, and the value."
  [arg argstail]
  (into (str/split arg #"=" 2) argstail))

(defn parse-args
  "Entry point to the technical work of turning a sequece of arguments
  `args` into a map that (maybe) conforms to a `schema`. It returns a
  map of the options as parsed according to the schema, but with two
  additional keys:

  - `::arguments` is a vector of application arguments, that is to say
    command-line arguments that do not represent an option value.

  - `::cli-args` is the raw, untouched vector of command-line
    arguments received as input. Perhaps you need it for some
    additional validation of positional logic."
  [schema args]
  (let [label+value-schemas (->> (value-schemas schema)
                                 (mapcat label+value-schema)
                                 (into {}))]
    ;; TODO Validate assumption on schema.
    (loop [options {}
           arguments []
           [arg & argstail] args]
      (cond
        (nil? arg) (assoc options ::arguments arguments ::cli-args args)
        (= "--" arg) (recur options (into arguments argstail) [])
        (re-seq #"^--\S+=" arg) (recur options arguments (break-long-option-and-value arg argstail))

        (or (re-seq #"^--\S+$" arg)
            (re-seq #"^-\S$" arg))
        (if-let [value-schema (get label+value-schemas arg)]
          (let [parsing-result (-parse-option value-schema options arg argstail)]
            (recur (.-options parsing-result)
                   arguments
                   (.-argstail parsing-result)))
          (recur (-> options
                     (update ::unknown-option-errors conj {:arg arg})
                     (assoc ::known-options (keys label+value-schemas)))
                 arguments
                 argstail))

        (re-seq #"^-\S+$" arg) (recur options arguments (break-short-option-group label+value-schemas arg argstail))
        :application-argument (recur options (conj arguments arg) argstail)))))

(def cli-args-transformer
  "The malli transformer wrapping `parse-args`. To be used it with
  `m/decode`, wrapped by `mt/transformer`. Merely turn a sequence of
  arguments `args` into a map that (maybe) conforms to a `schema`. You
  can compose this transformer to further refine command-line argument
  parsing. See `simple-cli-options-transformer` for an example."
  {:name :cli-args-transformer
   :compile (fn [schema _]
              (fn [args]
                (parse-args schema args)))})

(def simple-cli-options-transformer
  "Simple transformer for the most common use cases when you only want
  to get a (nested) map of options out of command-line arguments. The
  `mt/default-value-transformer` will fill the blank and
  `mt/strip-extra-keys-transformer` will remove any extraneous
  keys. Use it for dumb, do-what-I-mean cli args parsing."
  (mt/transformer
    cli-args-transformer
    mt/strip-extra-keys-transformer
    mt/default-value-transformer
    mt/string-transformer))
