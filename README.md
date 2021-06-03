# `malli-config-cli`

Command-line arguments with malli.

[![](https://img.shields.io/clojars/v/piotr-yuxuan/malli-config-cli.svg)](https://clojars.org/piotr-yuxuan/malli-config-cli)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/malli-config-cli)](https://cljdoc.org/d/piotr-yuxuan/malli-config-cli/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/malli-config-cli)](https://github.com/piotr-yuxuan/malli-config-cli/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/malli-config-cli)](https://github.com/piotr-yuxuan/malli-config-cli/issues)

This library provides out-of-the-box cli parsing. It exposes a
function which takes a vector of strings `args` as input and return a
map that can later be merged with the config. As such it intends to
play nicely with configuration tools like 1Config, so you may consider
configuration values from different sources:

1. Default configuration value;
2. Configuration as stored in 1Config;
3. One-off configuration overrides in your command-line arguments;

then you may merge these values to get the actual, concrete
configuration. For example with this config schema:

``` clojure
(def Config
  (m/schema
    [:map {:closed true}
     [:upload-api {:short-option "-a"
                   :default "http://localhost:8080"
                   :description "Address of target api instance."}
       string?]
     [:market {:default "MARKET_CODE"
               :description "Must be the string representation of a market code as defined in our data model."}
       [:enum "OUTER_SPACE" "MARKET_CODE"]]
     [:data [:map
       [:format {:decode/string keyword
                 :description "The type of the resource file to upload."}
         [:enum :line-json :line-edn]]
       [:file {:decode/string keyword
               :description "The type of the resource file to upload."}
         string?]]]
     [:proxy
       [:map
         [:host string?]
         [:port pos-int?]]]
     [:async-parallelism {:default 64
                          :description "Parallelism factor for `core.async`."}
       pos-int?]]))
```

and this config value retrieved from your configuration system:

``` clojure
{:upload-api "https://example.com/upload"
 :proxy {:host "https://proxy.example.com"
         :port 3128}}
```

you can invoke your Clojure main function with:

``` zsh
lein run -m upload-job \
  --market OUTER_SPACE \
  --upload-data-format line-json \
  --upload-data-file ./latest-data-file.edn
```

and the resulting configuration passed to your app will be:

``` clojure
{;; Set as the default value
 :async-parallelism 64
 ;; Set from your configuration system
 :upload-api "https://example.com/upload"
 :proxy {;; Nested config maps are supported
         :host "http://proxy.example.com"
         :port 3128}
 ;; Set from command-line overrides
 :market "OUTER_SPACE"
 :upload/data {;; Namespaced keywords are supported, too.
               :format :line-json
               :file "./latest-data-file.edn"}}
```

From a technical point of view, it leverages malli coercion and
decoding capabilities so that you may define the shape of your
configuration and default value in one place, then derive a
command-line interface from it.

``` clojure
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
    (malli-config-cli/parse args)))

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
