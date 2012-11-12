(defproject com.redhat.qe/extended-selenium "1.1.0-SNAPSHOT"
  :description "An extension of the selenium RC client with extra logging and convenience methods"
  :java-source-path "src"
  :dependencies [[org.seleniumhq.selenium.client-drivers/selenium-java-client-driver "1.0.2"]
                 [com.redhat.qe/jul.test.records "1.0.0"]]
  :javac-options {:debug "on"}
  :plugins [[lein-eclipse "1.0.0"]])
