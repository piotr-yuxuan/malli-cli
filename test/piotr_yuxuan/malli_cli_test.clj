(ns piotr-yuxuan.malli-cli-test
  (:require [clojure.test :refer [deftest testing is]]
            [piotr-yuxuan.malli-cli :as malli-cli]
            [malli.core :as m]
            [malli.transform :as mt]
            [malli.util :as mu]))

(deftest children-successor-test
  (is (= (malli-cli/children-successor [:enum :a :b :c :d])
         {:a :b
          :b :c
          :c :d
          :d :d}))
  (is (= (malli-cli/children-successor [:enum {:default :b} :a :b :c :d])
         {:a :b
          :b :c
          nil :c
          :c :d
          :d :d})))

(deftest kw-ns-test
  (is (= ["a"] (malli-cli/name-items :a)))
  (is (= ["a" "b"] (malli-cli/name-items :a/b)))
  (is (= ["a" "b" "c" "d"] (mapcat malli-cli/name-items [:a :b/c :d])))
  (is (= ["0" "a" "b" "c" "d" "e"] (mapcat malli-cli/name-items [0 :a :b/c #"d" 'e]))))

(deftest config-option-schemas-test
  (let [type-schemas (malli-cli/value-schemas [:map
                                               [:a [:map
                                                    [:b int?]
                                                    [:c int?]]]
                                               [:d int?]])]
    (doseq [schema (map :schema type-schemas)]
      ;; Too bad we can't use `=`.
      (is (mu/equals schema int?)))
    (is (= (map #(dissoc % :schema) type-schemas)
           [{:path [:a :b], :in [:a :b]}
            {:path [:a :c], :in [:a :c]}
            {:path [:d], :in [:d]}]))))

(deftest long-and-short-labels-test
  (let [schema [:map
                [:node-0 [:map
                          [:leaf-0-0 int?]
                          [:leaf-0-1 int?]]]
                [:node-1 int?]]]
    (is (= (->> (malli-cli/value-schemas schema)
                (mapcat malli-cli/label->value-schema)
                (into {})
                keys)
           ["--node-0-leaf-0-0" "--node-0-leaf-0-1" "--node-1"])))
  (let [schema [:map
                [:node-0 [:map
                          [:leaf-0-0 [int? {:long-option "--youp"}]]
                          [:default-long-option-name [int? {:short-option "-y"}]]]]
                [:node-1 [int? {:long-option "--foo"
                                :short-option "-b"}]]]]
    (is (= (->> (malli-cli/value-schemas schema)
                (mapcat malli-cli/label->value-schema)
                (into {})
                keys)
           ["--youp" "-y" "--node-0-default-long-option-name" "-b" "--foo"]))))

(deftest parse-option-test
  (testing "top-level option"
    (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:my-long-option [:enum :a :b :c :d]]])
                                    (mapcat malli-cli/label->value-schema)
                                    (into {}))
          options {:existing {:parsed "options"}}
          [arg & rest-args] ["--my-long-option" "a" "--other-options" "and" "args"]
          [options rest-args] (malli-cli/-parse-option (get label->value-schemas arg) options arg rest-args)]
      (is (= options {:existing {:parsed "options"}, :my-long-option "a"}))
      (is (= rest-args '("--other-options" "and" "args")))))
  (testing "nested option"
    (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:my-long [:map [:nested-option int?]]]])
                                    (mapcat malli-cli/label->value-schema)
                                    (into {}))
          options {:existing {:parsed "options"}}
          [arg & rest-args] ["--my-long-nested-option" 1 "--other-options" "and" "args"]
          [options rest-args] (malli-cli/-parse-option (get label->value-schemas arg) options arg rest-args)]
      (is (= options {:existing {:parsed "options"}, :my-long {:nested-option 1}}))
      (is (= rest-args '("--other-options" "and" "args")))))
  (testing "multiple arguments"
    (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:my-option [string? {:arg-number 2}]]])
                                    (mapcat malli-cli/label->value-schema)
                                    (into {}))
          options {:existing {:parsed "options"}}
          [arg & rest-args] ["--my-option" "a" "b" "--other-options" "and" "args"]
          [options rest-args] (malli-cli/-parse-option (get label->value-schemas arg) options arg rest-args)]
      (is (= options {:existing {:parsed "options"}, :my-option '("a" "b")}))
      (is (= rest-args '("--other-options" "and" "args")))))
  (testing "with update-fn"
    (testing "with multiple arguments"
      (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:my-option [string? {:arg-number 2
                                                                                           :update-fn (fn [options {:keys [path]} cli-args]
                                                                                                        (assoc-in options path (vec (reverse cli-args))))}]]])
                                      (mapcat malli-cli/label->value-schema)
                                      (into {}))
            options {:existing {:parsed "options"}}
            [arg & rest-args] ["--my-option" "a" "b" "--other-options" "and" "args"]
            [options rest-args] (malli-cli/-parse-option (get label->value-schemas arg) options arg rest-args)]
        (is (= options {:existing {:parsed "options"}, :my-option ["b" "a"]}))
        (is (= rest-args '("--other-options" "and" "args")))))
    (testing "transforming the value in update-fn"
      (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:my-option [string? {:update-fn (fn [options {:keys [path]} [cli-arg]]
                                                                                                        (assoc-in options path (str "grr-" cli-arg)))}]]])
                                      (mapcat malli-cli/label->value-schema)
                                      (into {}))
            options {:existing {:parsed "options"}}
            [arg & rest-args] ["--my-option" "a" "--other-options" "and" "args"]
            [options rest-args] (malli-cli/-parse-option (get label->value-schemas arg) options arg rest-args)]
        (is (= options {:existing {:parsed "options"}, :my-option "grr-a"}))
        (is (= rest-args '("--other-options" "and" "args"))))
      (testing "with short value"
        (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:a [string? {:short-option "-a"
                                                                                     :update-fn (fn [options {:keys [path]} [cli-arg]]
                                                                                                  (assoc-in options path (str "grr-" cli-arg)))}]]])
                                        (mapcat malli-cli/label->value-schema)
                                        (into {}))
              options {:existing {:parsed "options"}}
              [arg & rest-args] ["-a" "a" "--other-options" "and" "args"]
              [options rest-args] (malli-cli/-parse-option (get label->value-schemas arg) options arg rest-args)]
          (is (= options {:existing {:parsed "options"}, :a "grr-a"}))
          (is (= rest-args '("--other-options" "and" "args"))))))
    (testing "accumulating values"
      (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:files [string? {:short-option "-f"
                                                                                       :long-option "--file"
                                                                                       :update-fn (fn [options {:keys [path]} [file]]
                                                                                                    (update-in options path conj file))}]]])
                                      (mapcat malli-cli/label->value-schema)
                                      (into {}))]
        (testing "with long option"
          (let [options {:existing {:parsed "options"}}
                [arg & rest-args] ["--file" "file://my-file" "--other-options" "and" "args"]
                [options rest-args] (malli-cli/-parse-option (get label->value-schemas arg) options arg rest-args)]
            (is (= options {:existing {:parsed "options"}, :files '("file://my-file")}))
            (is (= rest-args '("--other-options" "and" "args")))
            (testing "as well as with short options"
              (let [[arg & rest-args] ["-f" "file://your-file" "--other-options" "and" "args"]
                    [options rest-args] (malli-cli/-parse-option (get label->value-schemas arg) options arg rest-args)]
                (is (= options {:existing {:parsed "options"}, :files '("file://your-file" "file://my-file")}))
                (is (= rest-args '("--other-options" "and" "args")))))))))))

(deftest break-short-option-group-test
  (testing "with no arguments"
    (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:verbose [string? {:short-option "-v"
                                                                                       :arg-number 0
                                                                                       :update-fn (fn [options {:keys [path]} _]
                                                                                                    (update-in options path (fnil inc 0)))}]]])
                                    (mapcat malli-cli/label->value-schema)
                                    (into {}))
          [arg & rest-args] ["-vvv" "--other-options" "and" "args"]]
      (is (= (malli-cli/break-short-option-group label->value-schemas
                                                 arg
                                                 rest-args)
             '("-v" "-v" "-v" "--other-options" "and" "args")))))
  (testing "with one argument"
    (let [label->value-schemas (->> (malli-cli/value-schemas [:map [:files [string? {:short-option "-f"
                                                                                     :update-fn (fn [options {:keys [path]} [file]]
                                                                                                  (update-in options path conj file))}]]])
                                    (mapcat malli-cli/label->value-schema)
                                    (into {}))
          [arg & rest-args] ["-fff" "file://my-file" "file://your-file" "file://another-file" "--other-options" "and" "args"]]
      (is (= (malli-cli/break-short-option-group label->value-schemas
                                                 arg
                                                 rest-args)
             '("-f" "file://my-file" "-f" "file://your-file" "-f" "file://another-file" "--other-options" "and" "args"))))))

(def MyCliSchema
  (m/schema
    [:map {;; When not defined, the map is turned into standard summary.
           :summary-fn nil
           :decode/cli-args-transformer malli-cli/cli-args-transformer}
     [:help [boolean? {:short-option "-h"
                       :default false
                       :arg-number 0}]]
     [:log-level [:and
                  keyword?
                  [:enum {:short-option "-v"
                          :short-option/arg-number 0
                          :short-option/update-fn (fn [options {:keys [in schema]} _cli-args]
                                                    (update-in options in (malli-cli/children-successor schema)))
                          :default :info}
                   :off :fatal :error :warn :info :debug :trace :all]]]
     [:upload-api [string?
                   {:short-option "-a"
                    :default "http://localhost:8080"
                    :description "Address of target upload-api instance."}]]
     [:database [string?
                 {:default "http://localhost:8888"
                  :description "Address of database instance behind upload-api."}]]
     [:markets [:vector
                [:enum {:decode/string keyword
                        :long-option "--market"
                        :update-fn (fn [options {:keys [in]} [market]]
                                     (update-in options in conj market))
                        :default :FRANCE
                        :description "Must be the string representation of a market code as from Viooh data model."}
                 :FRANCE :UNITED_KINGDOM]]]
     [:upload/data [:map
                    [:format string?]
                    [:file string?]]]
     [:proxy [:map
              [:host string?]
              [:port pos-int?]]]
     [:async-parallelism [pos-int? {:default 64
                                    :description "Parallelism factor for `core.async`."}]]
     [:create-market-dataset [boolean? {:default false
                                        :description "If true, needs `--database` to be set. It will create the dataset. Canary test will be performed after the version is published, because database needs at least one version to have been published before it can respond."}]]]))

(deftest cli-args-transformer-test
  (is (= (m/decode MyCliSchema [] (mt/transformer malli-cli/cli-args-transformer))
         #:piotr-yuxuan.malli-cli{:arguments [], :cli-args []}))
  (let [cli-args ["-vvv" "--upload-api" "http://localhost:8080" "--market" "FRANCE" "--market=UNITED_KINGDOM" "random-arg" "--upload-data-file" "samples/primary.edn" "--upload-data-format" "line-edn" "--proxy-host" "localhost" "--proxy-port" "8081" "--" "little" "weasel"]]
    (is (= (m/decode MyCliSchema cli-args (mt/transformer malli-cli/cli-args-transformer))
           {:log-level :all,
            :upload-api "http://localhost:8080",
            :markets '("UNITED_KINGDOM" "FRANCE"),
            :upload/data {:file "samples/primary.edn", :format "line-edn"},
            :proxy {:host "localhost", :port "8081"},
            :piotr-yuxuan.malli-cli/arguments ["random-arg" "little" "weasel"],
            :piotr-yuxuan.malli-cli/cli-args cli-args})))
  (let [cli-args ["-v" "--upload-api" "http://localhost:8080" "--market" "FRANCE" "--market=UNITED_KINGDOM" "random-arg" "--upload-data-file" "samples/primary.edn" "--upload-data-format" "line-edn" "--proxy-host" "localhost" "--proxy-port" "8081" "--" "little" "weasel"]]
    (is (= (m/decode MyCliSchema cli-args (mt/transformer malli-cli/cli-args-transformer))
           {:log-level :debug,
            :upload-api "http://localhost:8080",
            :markets '("UNITED_KINGDOM" "FRANCE"),
            :upload/data {:file "samples/primary.edn", :format "line-edn"},
            :proxy {:host "localhost", :port "8081"},
            :piotr-yuxuan.malli-cli/arguments ["random-arg" "little" "weasel"],
            :piotr-yuxuan.malli-cli/cli-args cli-args})))
  (is (= (m/decode MyCliSchema ["--help"] (mt/transformer malli-cli/cli-args-transformer))
         {:help true, :piotr-yuxuan.malli-cli/arguments [], :piotr-yuxuan.malli-cli/cli-args ["--help"]}))
  (let [cli-args ["--log-level" "all"]]
    (is (= (m/decode MyCliSchema cli-args (mt/transformer malli-cli/cli-args-transformer))
           {:log-level "all", :piotr-yuxuan.malli-cli/arguments [], :piotr-yuxuan.malli-cli/cli-args ["--log-level" "all"]}))
    (is (= (m/decode MyCliSchema cli-args (mt/transformer malli-cli/simple-cli-options-transformer))
           {:log-level :all,
            :help false,
            :upload-api "http://localhost:8080",
            :database "http://localhost:8888",
            :async-parallelism 64,
            :create-market-dataset false}))))
