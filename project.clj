(defproject longadeseo "0.1.0-SNAPSHOT"
  :repositories [["alfresco" "https://maven.alfresco.com/nexus/content/repositories/releases"]
                 ["alfresco-thirdparty" "https://maven.alfresco.com/nexus/content/repositories/thirdparty"]
                 ["alfresco-public" "https://maven.alfresco.com/nexus/content/repositories/public"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.alfresco/alfresco-jlan-embed "4.2.f"]]
  :main longadeseo.core)
