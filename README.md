# `malli-config-cli`

[![](https://img.shields.io/clojars/v/piotr-yuxuan/malli-config-cli.svg)](https://clojars.org/piotr-yuxuan/malli-config-cli)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/malli-config-cli)](https://cljdoc.org/d/piotr-yuxuan/malli-config-cli/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/malli-config-cli)](https://github.com/piotr-yuxuan/malli-config-cli/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/malli-config-cli)](https://github.com/piotr-yuxuan/malli-config-cli/issues)

TODO: write proper README.

Parse and command-line arguments with malli.

``` clojure
(defn load-config
  [& args]
  (let [retrieved-value (:value (configure {:key service-name
                                            :env (env)
                                            :version (version)}))
        config (deep-merge (edn/read-string (slurp (io/resource "default-value.edn")))
                           retrieved-value
                           (malli-config-cli/parse args))]
    (assert (m/validate Config config) (m/explain Config config))
    config))
```
