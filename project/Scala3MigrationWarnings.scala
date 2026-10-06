import sbt._
import sbt.Keys._

/** Silences, for Scala 3 only, the syntax deprecation warnings introduced after Scala 3.3 LTS for constructs which are
  * still needed by the sources shared with Scala 2.12/2.13.
  */
object Scala3MigrationWarnings extends AutoPlugin {
  override def requires = com.softwaremill.SbtSoftwareMillCommon
  override def trigger = allRequirements

  // TODO(scala-3.9): remove once Scala 2 is dropped (or once the shared sources use Scala 3-compatible syntax under
  // -Xsource:3), and migrate the code using `-rewrite -source 3.4-migration` / `-source 3.7-migration`
  private val suppressed = List(
    "msg=is deprecated for wildcard arguments of types:s", // `_` -> `?` in types (3.4)
    "msg=with as a type operator has been deprecated:s", // `A with B` -> `A & B` (3.4)
    "msg=is no longer supported for vararg splices:s", // `xs: _*` -> `xs*` (3.4)
    "msg=for eta-expansion is unnecessary:s", // `f _` -> `f` (3.4)
    "msg=use .= uninitialized. instead:s", // `var x: T = _` -> `= uninitialized` (3.4)
    "msg=Implicit parameters should be provided with a .using. clause:s" // `f(x)` -> `f(using x)` (3.7)
  ).map(f => s"-Wconf:$f")

  override def projectSettings: Seq[Setting[?]] = Seq(
    scalacOptions ++= (if (ScalaArtifacts.isScala3(scalaVersion.value)) suppressed else Nil)
  )
}
