(defproject com.github.piotr-yuxuan/malli-cli (-> "./resources/malli-cli.version" slurp .trim)
  :description "Configuration powertool with `metosin/malli`"
  :url "https://github.com/piotr-yuxuan/malli-cli"
  :license {:name "European Union Public License 1.2 or later"
            :url "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"
            :distribution :repo}
  :scm {:name "git"
        :url "https://github.com/piotr-yuxuan/malli-cli"}
  :pom-addition [:developers [:developer
                              [:name "胡雨軒 Петр"]
                              [:url "https://github.com/piotr-yuxuan"]]]
  :dependencies []
  :profiles {:github {:github/topics ["cli" "clojure" "command-line" "cli-app"
                                      "command-line-tool" "args-parser" "malli"
                                      "configuration" "configuration-management"
                                      "env-var" "environment-variable" "secret"
                                      "secret-management" "sourcing" "babashka"
                                      "bb" "graalvm" "command-line-parser"]
                      :github/private? false}
             :provided {:dependencies [[org.clojure/clojure "1.12.3"]
                                       [metosin/malli "0.19.1"]]}
             :dev {:global-vars {*warn-on-reflection* true}
                   :dependencies [[camel-snake-kebab "0.4.3"]]
                   :jvm-opts ["-Dclojure.compiler.direct-linking=false"]}
             :jar {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :kaocha [:test {:dependencies [[lambdaisland/kaocha "1.91.1392"]]}]}
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url "https://clojars.org/repo"
                                    :username :env/WALTER_CLOJARS_USERNAME
                                    :password :env/WALTER_CLOJARS_PASSWORD}]
                        ["github" {:sign-releases false
                                   :url "https://maven.pkg.github.com/piotr-yuxuan/malli-cli"
                                   :username :env/GITHUB_ACTOR
                                   :password :env/WALTER_GITHUB_PASSWORD}]])
