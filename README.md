# `malli-cli`

Command-line arguments with malli.

[![](https://img.shields.io/clojars/v/piotr-yuxuan/malli-cli.svg)](https://clojars.org/piotr-yuxuan/malli-cli)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/malli-cli)](https://cljdoc.org/d/piotr-yuxuan/malli-cli/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/malli-cli)](https://github.com/piotr-yuxuan/malli-cli/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/malli-cli)](https://github.com/piotr-yuxuan/malli-cli/issues)

Maturity notice: it is currently under development but can already
provide some help. The error handling should be improved before it is
considered more stable.

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

# Capabilities

See tests for minimal working code for each of these examples.

- Long option flag and value `--long-option VALUE` may give

``` clj
{:long-option "VALUE"}
```

- Grouped flag and value with `--long-option=VALUE` may give

``` clj
{:long-option "VALUE"}
```

- Short option names with `-s VALUE` may give

``` clj
{:some-option "VALUE"}
```

- Options that accept a variable number of arguments: `-a -b val0 --c
  val1 val2`

``` clj
{:option-a true
 :option-b "val0"
 :option-c ["val1" "val2"]}
```

- Non-option arguments are supported directly amongst options, or
  after a double-dash so `-a 1 ARG0 -b 2 -- ARG1 ARG2` may be
  equivalent to:

``` clj
{:option-a 1
 :option-b 2
 :piotr-yuxuan.malli-cli/arguments [ARG0 ARG1 ARG2]}
```

- Grouped short flags like `-hal` are expanded like, for example:
``` clj
{:help true
 :all true
 :list true}
```

- Non-idempotent options like `-vvv` are supported and may be rendered as:
``` clj
{:verbosity 3}
;; or, depending on what you want:
{:log-level :debug}
```

- You may use nested maps in your config schema so that `--proxy-host
  https://example.org/upload --proxy-port 3447` is expanded as:

``` clj
{:proxy {:host "https://example.org/upload"
         :port 3447}}
```

- Namespaced keyword are allowed, albeit the command-line option name
  stays simple `--upload-parallelism 32` may give:

```clj
{:upload/parallelism 32}
```

- You can provide your own code to update the result map with some
  complex behaviour, like for example `--name Piotr`:

``` clj
{:vanity-name ">> Piotr <<"
 :original-name "Piotr"
 :first-letter \P}
```

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
