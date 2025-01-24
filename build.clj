(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [clojure.tools.build.tasks.process :as p]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'exoscale/reporter)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def copy-srcs ["src"])
(def target-dir "target")
(def jar-file (format "%s/%s-%s.jar" target-dir (name lib) version))

(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean
  [opts]
  (b/delete {:path target-dir})
  opts)

(defn jar
  [opts]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs copy-srcs
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file})
  opts)

(defn deploy
  [opts]
  (dd/deploy {:artifact jar-file
              :pom-file (format "%s/classes/META-INF/maven/%s/pom.xml"
                                target-dir
                                lib)
              :installer :remote
              :sign-releases? false
              :repository "clojars"})
  opts)

(defn tag
  [opts]
  (p/process {:command-args
              ["sh" "-c" (format "git tag -a \"%s\" --no-sign -m \"Release %s\""
                                 version version)]})
  (p/process {:command-args ["sh" "-c" "git pull"]})
  (p/process {:command-args ["sh" "-c" "git push --follow-tags"]})
  opts)

(defn release
  [opts]
  (-> opts
      clean
      jar
      deploy
      tag))
