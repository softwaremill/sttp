// using '-coursier' because of https://github.com/lucidsoftware/neo-sbt-scalafmt/issues/64
addSbtPlugin("com.lucidchart" % "sbt-scalafmt-coursier" % "1.15")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.7")

addSbtPlugin("com.updateimpact" % "updateimpact-sbt-plugin" % "2.1.3")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.22")
