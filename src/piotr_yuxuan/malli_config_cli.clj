(ns piotr-yuxuan.malli-config-cli
  (:require [clojure.data]
            [malli.core :as m]
            [malli.util :as mu]
            [malli.transform :as mt]
            [clojure.string :as str]))

(def void
  any?)

(defn magic-code
  [schema args]
  {:a {:b {}}})

(def args->nested-map
  {:name :args->nested-map
   :compile (fn [schema _]
              (println :keys (map key (m/entries schema)))
              (fn [x]
                (magic-code schema x)))})

(defn summary
  [schema])

(letfn [(kw-ns [kw] (if-let [kw-ns (namespace kw)]
                      [kw-ns (name kw)]
                      [(name kw)]))
        (long-option [path map-entry] (->> (key map-entry)
                                           (conj path)
                                           (mapcat kw-ns)
                                           (str/join "-")
                                           (str "--")))
        (entry-schema [map-entry] (->> map-entry val m/-children first))
        (arg-options [{:keys [path schema]}] (->> (m/entries schema)
                                                  (remove (comp #{:map} m/type entry-schema))
                                                  (mapcat (comp #(apply interleave %)
                                                                (juxt (comp #(filter some? %)
                                                                            (juxt (comp :short-option m/properties val)
                                                                                  (partial long-option path)))
                                                                      (comp repeat key))))))]
  (defn schema->options->keys
    [schema]
    (->> (mu/subschemas schema)
         (filter (comp #{:map} m/type :schema))
         (mapcat arg-options)
         (partition 2)
         (map vec)
         (into {}))))

(schema->options->keys Schema)

(m/decode Schema
          args
          (mt/transformer
            {:name :args->nested-map}
            mt/default-value-transformer
            mt/strip-extra-keys-transformer))

(def easy-args->nested-map
  "Just an example usage." ; + validation
  (mt/transformer
    {:name :args->nested-map}
    mt/default-value-transformer
    mt/strip-extra-keys-transformer))

;; Basically, implement https://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html
;; zero-arg options
;; -v -v -v -v non-idempotent options
;; --file --file --file multiple instances
;; --[no-]daemon
;; --
;; -aeir expansion
;; apply validation before or after parsing (for decode, :enter or :leave chosen on :compile)
;; positional, depending on the current accumulated value.
;; Long option arguments may be specified with an equals sign.
;; Prevent :proxy-host to shadow [:proxy :host].
