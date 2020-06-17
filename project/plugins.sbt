libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "1.0.0"
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.1.0")
addSbtPlugin("org.scala-js" % "sbt-jsdependencies" % "1.0.1")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.0-M2")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.5.2")

val sbtSoftwareMillVersion = "1.9.7"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwareMillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwareMillVersion)

addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.4.1")
