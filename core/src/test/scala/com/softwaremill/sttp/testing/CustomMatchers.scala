package com.softwaremill.sttp.testing

import java.nio.file.{Files, Paths}
import java.{io, util}

import org.scalatest.matchers.{MatchResult, Matcher}

object CustomMatchers {
  class FileContentsMatch(file: java.io.File) extends Matcher[java.io.File] {
    override def apply(left: io.File): MatchResult = {
      val inBA = Files.readAllBytes(Paths.get(left.getAbsolutePath))
      val expectedBA = Files.readAllBytes(Paths.get(file.getAbsolutePath))
      MatchResult(
        util.Arrays.equals(inBA, expectedBA),
        "The files' contents are not the same",
        "The files' contents are the same"
      )
    }
  }

  def haveSameContentAs(file: io.File) = new FileContentsMatch(file)
}
