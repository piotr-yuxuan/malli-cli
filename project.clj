(defproject com.github.piotr-yuxuan/malli-cli (-> "./resources/malli-cli.version" slurp .trim)
  :description "Command-line interface from the comfort of a metosin/malli schema"
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
                                      "command-line-tool" "args-parser" "malli"]
                      :github/private? false}
             :provided {:dependencies [[org.clojure/clojure "1.12.0-alpha1"]
                                       [metosin/malli "0.10.1"]]}
             :dev {:global-vars {*warn-on-reflection* true}
                   :dependencies [[camel-snake-kebab "0.4.3"]]}
             :jar {:jvm-opts ["-Dclojure.compiler.disable-locals-clearing=false"
                              "-Dclojure.compiler.direct-linking=true"]}
             :kaocha [:test {:dependencies [[lambdaisland/kaocha "1.80.1274"]]}]}
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url "https://clojars.org/repo"
                                    :username :env/WALTER_CLOJARS_USERNAME
                                    :password :env/WALTER_CLOJARS_PASSWORD}]
                        ["github" {:sign-releases false
                                   :url "https://maven.pkg.github.com/piotr-yuxuan/malli-cli"
                                   :username :env/GITHUB_ACTOR
                                   :password :env/WALTER_GITHUB_PASSWORD}]])
