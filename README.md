# `piotr-yuxuan/malli-cli`

![](./doc/controller.jpg)

Command-line arguments with malli.

[![Clojars badge](https://img.shields.io/clojars/v/com.github.piotr-yuxuan/malli-cli.svg)](https://clojars.org/com.github.piotr-yuxuan/malli-cli)
[![cljdoc badge](https://cljdoc.org/badge/com.github.piotr-yuxuan/malli-cli)](https://cljdoc.org/d/com.github.piotr-yuxuan/malli-cli/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/malli-cli)](https://github.com/piotr-yuxuan/malli-cli/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/malli-cli)](https://github.com/piotr-yuxuan/malli-cli/issues)

You may also be interested in https://github.com/l3nz/cli-matic which
is more established.

# What it offers

This library provides out-of-the-box cli parsing. It exposes a
function which takes a vector of strings `args` as input and return a
map that can later be merged with the config. As such it intends to
play nicely with configuration tools, so you may have the following
workflow:

- Retrieve value from some configuration management system ;
- Consider command line arguments as configuration ad-hoc overrides,
  tokenise them and structure them into a map of command-line options;
- Merge the two partial values above and fill the blank with default
  values;
- If the resulting value conforms to what you expect (schema
  validation) you may finally use it with confidence.

# Maturity and evolution

> TL;DR: account for some changes ahead, but it is usable as is and
> should probably match your use case.

The API can be expected to change as we're still in version
`0.0.x`. However, given the examples below, one would say that it
covers all of the use cases one could think about regarding
command-line arguments.

However, a command-line interface is not restricted to arguments but
should also gracefully handle environement variables. It is currently
possible (see example below) but it could probably be made much
simpler and straightforward. Please your input on this if you have any
🙂!

# Simple example

Let's consider this config schema:

``` clojure
(require '[piotr-yuxuan.malli-cli :as malli-cli])
(require '[malli.core :as m])

(def Config
  (m/schema
    [:map {:closed true}
     [:help [boolean? {:short-option "-h"
                       :optional true
                       :arg-number 0}]]
     [:upload-api [string?
                   {:short-option "-a"
                    :default "http://localhost:8080"
                    :description "Address of target upload-api instance."}]]
     [:log-level [:enum {:decode/string keyword
                         :short-option "-v"
                         :short-option/arg-number 0
                         :short-option/update-fn (fn [options {:keys [path schema]} _cli-args]
                                                   (update-in options path (malli-cli/children-successor schema)))
                         :default :error}
                  :off :fatal :error :warn :info :debug :trace :all]]
     [:proxy [:map
              [:host string?]
              [:port pos-int?]]]]))
```

Also, here is what your configuration system provides:

``` clojure
{:upload-api "https://example.com/upload"
 :proxy {:host "https://proxy.example.com"
         :port 3128}}
```

You may invoke your Clojure main function with:

``` zsh
lein run \
  --help -vvv \
  -a "https://localhost:3004"
```

and the resulting configuration passed to your app will be:

``` clojure
{:help true
 :upload-api "https://localhost:3004"
 :log-level :debug
 :proxy {;; Nested config maps are supported
         :host "http://proxy.example.com"
         ;; malli transform strings into appropriate types
         :port 3128}
```

From a technical point of view, it leverages malli coercion and
decoding capabilities so that you may define the shape of your
configuration and default value in one place, then derive a
command-line interface from it.

``` clojure
(require '[piotr-yuxuan.malli-cli :as malli-cli])
(require '[malli.core :as m])

(defn load-config
  [args]
  (deep-merge
    ;; Default value
    (m/decode Config {} mt/default-value-transformer)
    ;; Value retrieved from configuration system
    (:value (configure {:key service-name
                        :env (env)
                        :version (version)}))
    ;; Command-line overrides
    (m/decode Config args malli-cli/simple-cli-options-transformer)))

(defn -main
  [& args]
  (let [config (load-config args)]
    (if (m/validate Config config)
      (app/start config)
      (do (log/error "Invalid configuration value"
                     (m/explain Config config))
          (Thread/sleep 60000) ; Leave some time to retrieve the logs.
          (System/exit 1)))))
```

# Capabilities

See tests for minimal working code for each of these examples.

- Long option flag and value `--long-option VALUE` may give

``` clj
{:long-option "VALUE"}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
  [:long-option string?]]
```

- Grouped flag and value with `--long-option=VALUE` may give

``` clj
{:long-option "VALUE"}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
  [:long-option string?]]
```

- Short option names with `-s VALUE` may give

``` clj
{:some-option "VALUE"}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:some-option [string? {:short-option "-s"}]]]
```

- Options that accept a variable number of arguments: `-a -b val0 --c
  val1 val2`

``` clj
{:a true
 :b "val0"
 :c ["val1" "val2"]}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:a [boolean? {:arg-number 0}]]
 [:b string?]
 [:c [string? {:arg-number 2}]]]
```

- Non-option arguments are supported directly amongst options, or
  after a double-dash so `-a 1 ARG0 -b 2 -- ARG1 ARG2` may be
  equivalent to:

``` clj
{:a 1
 :b 2
 :piotr-yuxuan.malli-cli/arguments ["ARG0" "ARG1" "ARG2"]}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:a [boolean? {:arg-number 0}]]
 [:b string?]]
```

- Grouped short flags like `-hal` are expanded like, for example:

``` clj
{:help true
 :all true
 :list true}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:help [boolean? {:short-option "-h" :arg-number 0}]]
 [:all [boolean? {:short-option "-a" :arg-number 0}]]
 [:list [boolean? {:short-option "-l" :arg-number 0}]]]
```

- Non-idempotent options like `-vvv` are supported and may be rendered as:

``` clj
{:verbosity 3}
;; or, depending on what you want:
{:log-level :debug}

;; Example schemas:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:log-level [:and
              keyword?
              [:enum {:short-option "-v"
                      :short-option/arg-number 0
                      :short-option/update-fn (fn [options {:keys [in schema]} _cli-args]
                                                (update-in options in (malli-cli/children-successor schema)))
                      :default :error}
               :off :fatal :error :warn :info :debug :trace :all]]]]

[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:verbosity [int? {:short-option "-v"
                    :short-option/arg-number 0
                    :short-option/update-fn (fn [options {:keys [in]} _cli-args]
                                              (update-in options in (fnil inc 0)))
                    :default 0}]]]
```

- You may use nested maps in your config schema so that `--proxy-host
  https://example.org/upload --proxy-port 3447` is expanded as:

``` clj
{:proxy {:host "https://example.org/upload"
         :port 3447}}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:proxy [:map
          [:host string?]
          [:port pos-int?]]]]
```

- Namespaced keyword are allowed, albeit the command-line option name
  stays simple `--upload-parallelism 32` may give:

```clj
{:upload/parallelism 32}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:upload/parallelism pos-int?]]
```

- You can provide your own code to update the result map with some
  complex behaviour, like for example `--name Piotr`:

``` clj
{:vanity-name ">> Piotr <<"
 :original-name "Piotr"
 :first-letter \P}

;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:vanity-name [string? {:long-option "--name"
                         :update-fn (fn [options {:keys [in]} [username]]
                                      (-> options
                                          (assoc :vanity-name (format ">> %s <<" username))
                                          (assoc :original-name username)
                                          (assoc :first-letter (first username))))}]]
 [:original-name string?]
 [:first-letter char?]]
```

- Build a simple summary string (see schema Config above):

``` txt
  -h  --help        nil
  -a  --upload-api  "http://localhost:8080"  Address of target upload-api instance.
  -v  --log-level   :error
      --proxy-host  nil
      --proxy-port  nil
```

- Error handling with unknown options:

``` clojure
;; Example schema:
[:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
 [:my-option string?]]

;; Example input:
["--unknown-long-option" "--other-option" "VALUE" "-s"}

;; Exemple output:
#:piotr-yuxuan.malli-cli{:unknown-option-errors ({:arg "-s"} {:arg "--other-option"} {:arg "--unknown-long-option"}),
                         :known-options ("--my-option"),
                         :arguments ["VALUE"],
                         :cli-args ["--unknown-long-option" "--other-option" "VALUE" "-s"]}
```

- Handle environment variables (good enough for now but subject to
  further API change):

``` clojure
;; Example schema.
(def Config
  [:map
   [:options
    [:map {:decode/cli-args-transformer malli-cli/cli-args-transformer}
     [:commands [:vector {:long-option "--command"
                          :update-fn (fn [options {:keys [in]} [command]]
                                       (update-in options in (fnil conj []) command))}
                 keyword?]]]]
   [:env
    [:map
     [:user string?]
     [:pwd string?]]]])

;; Example code. To keep it straightforward, config here only comes
;; from malli-cli. The `:env` map will be stripped of extra keys by the
;; transformer.
(require '[camel-snake-kebab.core :as csk])
(require '[malli.transform :as mt])
(defn load-config
  [env args]
  (m/decode Config
            {:options args
             :env (into {} env)}
            (mt/transformer
              (mt/key-transformer {:decode csk/->kebab-case-keyword})
              malli-cli/cli-args-transformer
              mt/strip-extra-keys-transformer
              mt/default-value-transformer
              mt/string-transformer)))

;; Example usage:
(load-config
  (System/getenv)
  ["--command" "init-db" "--command" "conform-repo"])
;; =>
  #_
  {:options {:commands [:init-db :conform-repo]},
   :env {:pwd "~",
         :user "piotr-yuxuan"}}

;; Another example usage, showing config validation:
(let [config (load-config
               (System/getenv)
               args)]
  (when-not (m/validate Config config)
    (pp/pprint (m/explain m/validate Config config))
    (System/exit 1)))
```
