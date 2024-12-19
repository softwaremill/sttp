import java.io.{File, PrintWriter}
import sbt.Logger

import java.nio.file.Path
import scala.io.Source

/** Generates the generated-docs/out/includes/examples_list.md file, basing on the metadata included in the source of
  * the examples.
  */
object GenerateListOfExamples {
  private val MetadataPattern = """// \{(.+?)}: (.+)""".r
  private val LinkBase = "https://github.com/softwaremill/sttp/tree/master"

  private case class Example(cat: String, otherMetadata: Map[String, String], description: String, path: Path)

  def apply(log: Logger, rootBasePath: File): Unit = {
    log.info(s"[GenerateListOfExamples] Base path: $rootBasePath")

    // from each of those examples, we need to extract the metadata
    val examples = FileUtils.listScalaFiles(rootBasePath.toPath.resolve("examples/src/main").toFile) ++
      FileUtils.listScalaFiles(rootBasePath.toPath.resolve("examples-ce2/src/main").toFile)

    val parsedExamples: Seq[Option[Example]] = for (example <- examples) yield {
      val first = firstLine(example)
      if (first.startsWith("// {")) {
        first match {
          case MetadataPattern(metadataStr, description) =>
            val metadata = metadataStr.trim
              .split(";")
              .map { entry =>
                val Array(key, value) = entry.split("=")
                key.trim -> value.trim
              }
              .toMap

            Some(Example(metadata("cat"), metadata - "cat", description, example))
        }
      } else {
        log.warn(s"[GenerateListOfExamples] Skipping $example as it doesn't start with the required prefix")
        None
      }
    }

    val examplesByCategory = parsedExamples.flatten.groupBy(_.cat)
    val renderedCategories = examplesByCategory.toList
      // we want the "Hello, World!" category to come first
      .sortBy { case (k, _) => if (k == "Hello, World!") "00" else k.toLowerCase() }
      .map { case (category, examples) =>
        val renderedExamplesList = examples
          .sortBy(_.description)
          // rendering a single line with the example's description & metadata
          .map { example =>
            val relativeLink = relativePath(example.path.toFile, rootBasePath)
            val tags = example.otherMetadata.toList
              .sortBy(_._1)
              .map { case (key, value) =>
                s"""<span class="example-tag example-${key.toLowerCase}">$value</span>"""
              }
              .mkString(" ")

            s"""* [${example.description}]($LinkBase/$relativeLink) $tags"""
          }
          // combining all examples in category
          .mkString("\n")

        s"""## $category
         |
         |$renderedExamplesList""".stripMargin
      }
      // separating categories
      .mkString("\n\n")

    // writing the result
    val targetPath = rootBasePath.toPath.resolve("generated-docs/out/includes/examples_list.md")
    ensureExists(targetPath.getParent.toFile)
    log.info(s"[GenerateListOfExamples] Writing rendered categories to $targetPath")

    val writer = new PrintWriter(targetPath.toFile)
    try writer.write(renderedCategories)
    finally writer.close()
  }

  private def firstLine(p: Path): String = {
    val source = Source.fromFile(p.toUri)
    try source.getLines().next()
    finally source.close()
  }

  private def relativePath(file: File, relativeTo: File): String = {
    val p1 = file.getAbsolutePath
    val p2 = relativeTo.getAbsolutePath
    if (!p1.startsWith(p2)) throw new IllegalArgumentException(s"$file is not relative to $relativeTo!")
    else p1.substring(p2.length)
  }

  private def ensureExists(dir: File): Unit = if (!dir.exists() && !dir.mkdirs()) {
    throw new IllegalStateException("Cannot create directory: " + dir)
  }
}
