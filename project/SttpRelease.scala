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
    releaseStepCommand("publishLocalSigned"),
    runClean,
    runTest,
    setReleaseVersion,
    updateVersionInDocs,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  )

  // based on https://github.com/EECOLOR/sbt-release-custom-steps/blob/master/src/main/scala/org/qirx/sbtrelease/UpdateVersionInFiles.scala
  private def updateVersionInDocs: ReleaseStep = { s: State =>
    val readmeFile = file("README.md")
    val readme = IO.read(readmeFile)

    val currentVersionPattern = """"com.softwaremill.sttp" %% "core" % "([\w\.-]+)"""".r
    val currentVersionInReadme = currentVersionPattern.findFirstMatchIn(readme).get.group(1)

    val releaseVersion = s.get(versions).get._1

    def replaceInFile(f: File): Unit = {
      s.log.info(s"Replacing $currentVersionInReadme with $releaseVersion in ${f.name}")

      val oldFile = IO.read(f)
      val newFile = oldFile.replaceAll(Pattern.quote(currentVersionInReadme), releaseVersion)
      IO.write(f, newFile)
    }

    def replaceRstInDirectory(d: File) {
      Option(d.listFiles()).foreach(_.foreach { f =>
        if (f.isDirectory) {
          replaceRstInDirectory(f)
        } else if (f.getName.endsWith(".rst")) {
          replaceInFile(f)
        }
      })
    }

    replaceInFile(readmeFile)
    replaceRstInDirectory(file("docs"))

    val settings = Project.extract(s)
    settings.get(releaseVcs).get.add(readmeFile.getAbsolutePath) !! s.log

    s
  }
}
