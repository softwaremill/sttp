package com.softwaremill.sttp.asynchttpclient.zio

import com.softwaremill.sttp._
import com.softwaremill.sttp.impl.zio._
import com.softwaremill.sttp.testing.SttpBackendStub
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import zio.{DefaultRuntime, Task}

class SttpBackendStubZioTests extends FlatSpec with Matchers with ScalaFutures {

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    implicit val b: SttpBackendStub[Task, Nothing] = SttpBackendStub(IOMonadAsyncError)
      .whenRequestMatches(_ => true)
      .thenRespondCyclic("a", "b", "c")

    // when
    val r = sttp.get(uri"http://example.org/a/b/c").send()

    // then
    val runtime = new DefaultRuntime {}
    runtime.unsafeRun(r).body shouldBe Right("a")
    runtime.unsafeRun(r).body shouldBe Right("b")
    runtime.unsafeRun(r).body shouldBe Right("c")
    runtime.unsafeRun(r).body shouldBe Right("a")
  }
}
