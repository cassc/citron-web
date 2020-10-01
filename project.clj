(defproject citron-web "0.1.3-SNAPSHOT"
  :description "Citron frontend"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.9.1"
  
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.238"]
                 [org.clojure/core.async  "0.4.474"]
                 [secretary "1.2.3"]
                 [alandipert/storage-atom "1.2.4"]
                 [cljs-ajax "0.7.3"]
                 [reagent "0.8.1"]
                 [reagent-utils "0.3.1" :exclusions [[reagent]]]
                 [nilenso/wscljs "0.2.0"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [com.taoensso/timbre "4.10.0"]]
  :infer-externs true
  
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "prod"]
            
            }


  :mirrors {#"clojars" {:name "clojars"
                        :url "https://mirrors.tuna.tsinghua.edu.cn/clojars"
                        :repo-manager true}}
  
  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.3"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]}}
  
  
  
  :source-paths ["src"]

  )
