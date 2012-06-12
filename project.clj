(defproject com.redhat.qe/extended-selenium "1.0.3.2"
  :description "An extension of the selenium RC client with extra logging and convenience methods"
  :java-source-path "src"
  :dependencies [[org.seleniumhq.selenium/selenium-java "2.23.1"]
                 [com.redhat.qe/jul.test.records "1.0.0"]]
  :javac-options {:debug "on"}
  :plugins [[lein-eclipse "1.0.0"]])
