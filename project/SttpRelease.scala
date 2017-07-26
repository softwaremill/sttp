import java.util.regex.Pattern

import sbt._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep
import sbtrelease.ReleaseStateTransformations._

object SttpRelease {
  def steps: Seq[ReleaseStep] = Seq(
    checkSnapshotDependencies,
    inquireVersions,
    // publishing locally so that the pgp password prompt is displayed early
    // in the process
    ReleaseStep(action = Command.process("publishLocalSigned", _)),
    runClean,
    runTest,
    setReleaseVersion,
    updateVersionInReadme,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
    pushChanges
  )

  // based on https://github.com/EECOLOR/sbt-release-custom-steps/blob/master/src/main/scala/org/qirx/sbtrelease/UpdateVersionInFiles.scala
  private def updateVersionInReadme: ReleaseStep = { s: State =>
    val readmeFile = file("README.md")
    val readme = IO.read(readmeFile)

    val currentVersionPattern = """"com.softwaremill.sttp" %% "core" % "([\w\.-]+)"""".r
    val currentVersionInReadme = currentVersionPattern.findFirstMatchIn(readme).get.group(1)

    val releaseVersion = s.get(versions).get._1

    s.log.info(s"Replacing $currentVersionInReadme with $releaseVersion in ${readmeFile.name}")

    val newReadme = readme.replaceAll(Pattern.quote(currentVersionInReadme), releaseVersion)
    IO.write(readmeFile, newReadme)

    val settings = Project.extract(s)
    settings.get(releaseVcs).get.add(readmeFile.getAbsolutePath) !! s.log

    s
  }
}
