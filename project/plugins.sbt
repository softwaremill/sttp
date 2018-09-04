addSbtPlugin("com.updateimpact" % "updateimpact-sbt-plugin" % "2.1.3")

libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "0.2.0"
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.5.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.25")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "0.5.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.3.8")

addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill" % "1.3.11")
