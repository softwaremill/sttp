addSbtPlugin("com.updateimpact" % "updateimpact-sbt-plugin" % "2.1.3")

libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "0.3.0"
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.26")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "0.6.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.3.9")

addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill" % "1.6.0")
