(ns piotr-yuxuan.malli-cli
  (:require [piotr-yuxuan.malli-cli.domain.gnu :as gnu]
            [piotr-yuxuan.malli-cli.domain.posix :as posix]
            [piotr-yuxuan.malli-cli.malli :as m']
            [piotr-yuxuan.malli-cli.utils :refer [remove-key -make-format]]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.transform :as mt])
  (:import (clojure.lang MapEntry)))

(defn children-successor
  "Given a schema `[:enum :a :b :c :d]`, return a Clojure map (that is,
  a queryable data structure) that returns the next item of
  :a -> :b -> :c -> :d <-> :d. The last item is mapped onto itself. If
  the schema has a default value as a property, like in
  `[:enum {:default :b} :a :b :c :d]` an additional mapping will be
  made nil -> :c.

  Primarily intended to be used on enum schema for non-idempotent
  options (like :verbose), but code is generic so you might think of a
  use case on another schema type."
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

(def non-idempotent-option
  (fn [options {:keys [in schema]} _cli-args]
    (update-in options in (children-successor schema))))

(defn name-items
  "Take an argument and return a vector of items that will form an
  option name. For example the option name for `:a/b` will be a-b."
  [x]
  (cond (not (keyword? x)) [(str x)]
        (namespace x) [(namespace x) (name x)]
        :else [(name x)]))

(def default-arg-number
  1)

(defn ^MapEntry long-option->value-schema
  [default-label value-schema]
  (when-let [long-option (:long-option value-schema (when (< 1 (count default-label))
                                                      ;; Character ? is reserved in shell
                                                      (str "--" (str/replace default-label #"\?$" ""))))]
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
        (zero? arg-number) (ParsingResult. (assoc-in options in true)
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
  "Entry point to the technical work of turning a sequence of arguments
  `args` into a map that (maybe) conforms to a `schema`. It returns a
  map of the options as parsed according to the schema, but with two
  additional keys:

  - `::operands` is a vector of application arguments, that is to say
    command-line arguments that do not represent an option value.

  - `::cli-args` is the raw, untouched vector of command-line
    arguments received as input. Perhaps you need it for some
    additional validation of positional logic."
  [label+value-schemas args]
  ;; TODO Validate assumption on schema.
  (loop [options {}
         operands []
         [arg & argstail] args]
    (cond
      (nil? arg) ; Argument list to parse is exhausted
      (assoc options
        ::operands operands
        ::cli-args args)

      (= posix/option-terminator arg)
      (recur options (into operands argstail) [])

      (gnu/long-option-with-value? arg)
      (recur options
             operands
             (break-long-option-and-value arg argstail))

      (and (get label+value-schemas arg)
           (or (gnu/long-option-without-value? arg)
               (posix/single-option-without-value? arg)))
      (let [parsing-result (-parse-option (get label+value-schemas arg) options arg argstail)]
        (recur (.-options parsing-result)
               operands
               (.-argstail parsing-result)))

      (or (gnu/long-option-without-value? arg)
          (posix/single-option-without-value? arg))
      (recur (-> options
                 (update ::unknown-option-errors conj {:arg arg})
                 (assoc ::known-options (keys label+value-schemas)))
             operands
             argstail)

      (posix/grouped-options? arg)
      (recur options
             operands
             (break-short-option-group label+value-schemas arg argstail))

      :operand
      (recur options
             (conj operands arg)
             argstail))))

(def args-transformer
  "The malli transformer wrapping `parse-args`. To be used it with
  `m/decode`, wrapped by `mt/transformer`. Merely turn a sequence of
  arguments `args` into a map that (maybe) conforms to a `schema`. You
  can compose this transformer to further refine command-line argument
  parsing. See `simple-cli-transformer` for an example."
  {:name :args-transformer
   :compile (fn [schema _]
              (let [label+value-schemas (->> (m'/value-schemas schema)
                                             (mapcat label+value-schema)
                                             (into {}))]
                (fn [args]
                  (parse-args label+value-schemas
                              args))))})

(defn ^:dynamic *system-get-env*
  []
  (System/getenv))

(def cli-transformer
  "Use it for dumb, do-what-I-mean cli args parsing. Simple transformer
  for the most common use cases when you only want to get a (nested)
  map of options out of command-line arguments:

  - `mt/strip-extra-keys-transformer` will remove any extraneous keys;

  - `m'/default-value-transformer` with `:env-var` injects environment
    variables (read at decode time);

  - `(m'/default-value-transformer {:key :default})` fills the blank
    with default values when applicable."
  (mt/transformer
    args-transformer
    mt/strip-extra-keys-transformer ; Remove it for debug, or more advanced usage.
    mt/string-transformer
    (mt/default-value-transformer {:key :env-var
                                   :default-fn #(get (*system-get-env*) %2)})
    (mt/default-value-transformer {:key :default})))

(defn start-with?
  "Return true if the collection `path` starts with all the items of
  collection `prefix`."
  [prefix path]
  (every? true? (map = prefix path)))

(defn prefix-shadowing
  [value-schemas]
  (loop [[{:keys [schema] :as head} & tail] value-schemas
         known-prefix? #{}
         remainder value-schemas]
    (let [prefix (some-> schema m/properties :summary-path)]
      (cond (nil? head) remainder
            (and prefix (known-prefix? prefix)) (recur tail known-prefix? remainder)
            prefix (recur tail
                          (conj known-prefix? prefix)
                          (remove (comp (partial start-with? prefix) :path) remainder))
            :else (recur tail known-prefix? remainder)))))

(def summary-header
  ["Short" "Long option" "Default" "Description"])

(defn default-value
  [value-schema]
  (let [default->str (or (-> value-schema first second :default->str)
                         (fn [x] (when-not (nil? x) (pr-str x))))
        default (-> value-schema first second :default)]
    (default->str default)))

(defn summary
  [schema]
  (let [short-option-name (comp first second)
        long-option-name (comp first first)
        description (comp #(->> % :description (:summary %)) second first)
        summary-table (->> (m'/value-schemas schema)
                           prefix-shadowing
                           (map label+value-schema)
                           ;; All through `str` so that nil is rendered as empty string.
                           (map (juxt (comp str short-option-name)
                                      (comp str long-option-name)
                                      (comp str default-value)
                                      (comp str description)))
                           (cons summary-header))
        max-column-widths (reduce (fn [acc row] (map (partial max) (map count row) acc))
                                  (repeat 0)
                                  summary-table)]
    (str/join "\n" (map (fn [v]
                          (let [fmt (-make-format max-column-widths)]
                            (str/trimr (apply format fmt v))))
                        summary-table))))
