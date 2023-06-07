# `piotr-yuxuan/malli-cli`

![](./doc/controller.jpg)

Configuration powertool with `metosin/malli`.


[![Build Status](https://github.com/piotr-yuxuan/malli-cli/actions/workflows/walter-cd.yml/badge.svg)](https://github.com/piotr-yuxuan/malli-cli/actions/workflows/walter-cd.yml)
[![Clojars badge](https://img.shields.io/clojars/v/com.github.piotr-yuxuan/malli-cli.svg)](https://clojars.org/com.github.piotr-yuxuan/malli-cli)
[![Clojars downloads](https://img.shields.io/clojars/dt/com.github.piotr-yuxuan/malli-cli)](https://clojars.org/com.github.piotr-yuxuan/malli-cli)
[![cljdoc badge](https://cljdoc.org/badge/com.github.piotr-yuxuan/malli-cli)](https://cljdoc.org/d/com.github.piotr-yuxuan/malli-cli/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/malli-cli)](https://github.com/piotr-yuxuan/malli-cli/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/malli-cli)](https://github.com/piotr-yuxuan/malli-cli/issues)
<a href="https://babashka.org" rel="nofollow"><img src="https://github.com/babashka/babashka/raw/master/logo/badge.svg" alt="bb compatible" style="max-width: 100%;"></a>

# What it offers

This library provides out-of-the-box:

- Configuration sourcing from command line arguments, environment variables, or
  any other configuration management tool you want to integrate with;
- Very expressive means to describe the configuration you expect;
- An agnostic way to protect secrets in your config that turn them into opaque,
  non-printable values, while still being able to decode them and access the
  original value when need be;
- Seamless and powerful CLI generation based on your config schema, returns a
  map representing the parsed, decoded arguments and environment variables you
  are interested in.

The returned map can be used as a config fragment, that you can later merge with
the config value provided by any other system. As such it intends to play nicely
with configuration tools, so the actual configuration value of your program is a
map that is a graceful merge of several overlapping config fragment:

1. Default configuration value;
2. Environment variables when the program starts up;
3. Value from some configuration management system;
4. Command line arguments.

The expected shape of your configuration being described as a malli schema so
you can parse and decode strings as well as validating any constraints. It's
quite powerful.

# Maturity and evolution

Break versioning is used, so no breaking changes will be introduced without
incrementing the major version. Some bug fixes may be introduced and I will keep
adding new features as I encounter new needs. As illustrated below, `malli-cli`
should already cover most of your use cases with simplicity – or open an issue.

# Naming

``` txt
utility_name [-a][-b][-c option_argument]
             [-d|-e][-f[option_argument]][operand...]
```

The utility in the example is named `utility_name`. It is followed by options,
option-arguments, and operands. The arguments that consist of
`-` characters and single letters or digits, such as `a`, are known as
"options" (or, historically, "flags"). Certain options are followed by an "
option-argument", as shown with `[ -c option_argument ]`. The arguments
following the last options and option-arguments are named
"operands".

# Simple example

Let's consider this config schema:

``` clojure
(require '[piotr-yuxuan.malli-cli :as malli-cli])
(require '[malli.core :as m])

(def Config
  (m/schema
    [:map {:closed true, :decode/args-transformer malli-cli/args-transformer}
     [:show-config? {:optional true}
      [boolean? {:description "Print actual configuration value and exit."
                 :arg-number 0}]]
     [:help {:optional true}
      [boolean? {:description "Display usage summary and exit."
                 :short-option "-h"
                 :arg-number 0}]]
     [:upload-api [string? {:description "Address of target upload-api instance. If not set from the command line, lookup env var $CORP_UPLOAD_API."
                            :short-option "-a"
                            ;; Cli option will be looked up, then env var, then default.
                            :env-var "CORP_UPLOAD_API"
                            :default "http://localhost:8080"}]]
     [:log-level [:enum {:description "Non-idempotent -v increases log level, --log-level sets it."
                         ;; Express how to decode a string into an enum value.
                         :decode/string keyword
                         :short-option "-v"
                         :short-option/arg-number 0
                         :short-option/update-fn malli-cli/non-idempotent-option
                         :default :error
                         ;; Used in summary to pretty-print the default value to a string.
                         :default->str name}
                  :off :fatal :error :warn :info :debug :trace :all]]
     [:proxy [:map
              [:host string?]
              ;; malli will parse and return an integer.
              [:port pos-int?]]]]))
```

Here is the command summary produced by `(malli-cli/summary Config)`
for this config:

``` txt
  Short  Long option    Default                  Description
         --show-config                           Print actual configuration value and exit.
  -h     --help                                  Display usage summary and exit.
  -a     --upload-api   "http://localhost:8080"  Address of target upload-api instance. If not set from the command line, lookup env var $CORP_UPLOAD_API.
  -v     --log-level    error                    Non-idempotent -v increases log level, --log-level sets it.
         --proxy-host
         --proxy-port
```

Let's try to call this program (code details below). You may invoke your Clojure
main function with:

``` zsh
lein run \
  --help -vvv \
  -a "https://localhost:4000"
```

Let's suppose your configuration system provides this value:

``` clojure
{:proxy {:host "https://proxy.example.com"
         :port 3128}}
```

and the shell environment variable `CORP_UPLOAD_API` is set to
`https://localhost:7000`. Then the resulting configuration passed to your app
will be:

``` clojure
{:help true
 :upload-api "https://localhost:4000"
 :log-level :debug
 :proxy {;; Nested config maps are supported
         :host "http://proxy.example.com"
         ;; malli transform strings into appropriate types
         :port 3128}
```

Let's try another time with this command with same provided config and env vars:

``` zsh
lein run \
  --log-level=off
  --show-config
```

The program will exit after printing:

``` clojure
{:log-level :off,
 :show-config? true,
 :upload-api "http://localhost:7000"}
```

---

From a technical point of view, it leverages malli coercion and decoding
capabilities so that you may define the shape of your configuration and default
value in one place, then derive a command-line interface from it.

``` clojure
(require '[piotr-yuxuan.malli-cli :as malli-cli])
(require '[malli.core :as m])
(require '[clojure.pprint])
(require '[piotr-yuxuan.malli-cli.utils :refer [deep-merge]])

(defn load-config
  [args]
  (deep-merge
    ;; Value retrieved from any configuration system you want
    (:value (configure {:key service-name
                        :env (env)
                        :version (version)}))
    ;; Command-line arguments, env-vars, and default values.
    (m/decode Config args malli-cli/cli-transformer)))

(defn -main
  [& args]
  (let [config (load-config args)]
    (cond (not (m/validate Config config))
          (do (log/error "Invalid configuration value"
                         (m/explain Config config))
              (Thread/sleep 60000) ; Leave some time to retrieve the logs.
              (System/exit 1))

          (:show-config? config)
          (do (clojure.pprint/pprint config)
              (System/exit 0))

          (:help config)
          (do (println (simple-summary Config))
              (System/exit 0))

          :else
          (app/start config))))
```

# Capabilities

See tests for minimal working code for each of these examples.

- Long option flag and value `--long-option VALUE` may give

``` clj
{:long-option "VALUE"}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
  [:long-option string?]]
```

- Grouped flag and value with `--long-option=VALUE` may give

``` clj
{:long-option "VALUE"}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
  [:long-option string?]]
```

- Short option names with `-s VALUE` may give

``` clj
{:some-option "VALUE"}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
 [:some-option [string? {:short-option "-s"}]]]
```

- Options that accept a variable number of arguments: `-a -b val0 --c val1 val2`

``` clj
{:a true
 :b "val0"
 :c ["val1" "val2"]}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
 [:a [boolean? {:arg-number 0}]]
 [:b string?]
 [:c [string? {:arg-number 2}]]]
```

- Non-option arguments are supported directly amongst options, or after a
  double-dash so `-a 1 ARG0 -b 2 -- ARG1 ARG2` may be equivalent to:

``` clj
{:a 1
 :b 2
 :piotr-yuxuan.malli-cli/arguments ["ARG0" "ARG1" "ARG2"]}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
 [:a [boolean? {:arg-number 0}]]
 [:b string?]]
```

- Grouped short flags like `-hal` are expanded like, for example:

``` clj
{:help true
 :all true
 :list true}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
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
[:map {:decode/args-transformer malli-cli/args-transformer}
 [:log-level [:and
              keyword?
              [:enum {:short-option "-v"
                      :short-option/arg-number 0
                      :short-option/update-fn malli-cli/non-idempotent-option
                      :default :error}
               :off :fatal :error :warn :info :debug :trace :all]]]]

[:map {:decode/args-transformer malli-cli/args-transformer}
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
[:map {:decode/args-transformer malli-cli/args-transformer}
 [:proxy [:map
          [:host string?]
          [:port pos-int?]]]]
```

- Namespaced keyword are allowed, albeit the command-line option name stays
  simple `--upload-parallelism 32` may give:

```clj
{:upload/parallelism 32}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
 [:upload/parallelism pos-int?]]
```

- You can provide your own code to update the result map with some complex
  behaviour, like for example `--name Piotr`:

``` clj
{:vanity-name ">> Piotr <<"
 :original-name "Piotr"
 :first-letter \P}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
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
[:map {:decode/args-transformer malli-cli/args-transformer}
 [:my-option string?]]

;; Example input:
["--unknown-long-option" "--other-option" "VALUE" "-s"}

;; Exemple output:
#:piotr-yuxuan.malli-cli{:unknown-option-errors ({:arg "-s"} {:arg "--other-option"} {:arg "--unknown-long-option"}),
                         :known-options ("--my-option"),
                         :arguments ["VALUE"],
                         :cli-args ["--unknown-long-option" "--other-option" "VALUE" "-s"]}
```

- Environment variable `USER` set to `piotr-yuxuan` may give:

``` clojure
{:user "piotr-yuxuan"}

;; Example schema:
[:map {:decode/args-transformer malli-cli/args-transformer}
 [:user [string? {:env-var "USER"}]]]
```

- Protect secret values and keep them out of logs when configuration is printed.
  Secrets are turned into strings by default, but you may provide
  custom `secret-fn` and `plaintext-fn`.

``` clojure
(m/encode
  [:map
   [:ui/service-name string?]
   [:database/username {:secret true} string?]
   [:database/password {:secret true} string?]]
  {:ui/service-name "Hello, world!"
   :database/username "Username must stay out of logs."
   :database/password "Password must stay out of logs."}
  malli-cli/secret-transformer)

=> {:ui/service-name "Hello, world!",
    :database/username "***",
    :database/password "***"}
```

Note that environment variables behave like default values with lower priority
than command-line arguments. Env vars are resolved at decode time, not at schema
compile time. This lack of purity is balanced by the environment being constant
and set by the JVM at start-up time.

# References

- [GNU Program Argument Syntax Conventions](https://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html)
- [POSIX.1-2017 Utility argument syntax](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html)
- https://github.com/l3nz/cli-matic is a similar, more established project.
