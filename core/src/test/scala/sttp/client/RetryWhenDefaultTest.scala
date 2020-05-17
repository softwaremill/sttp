package sttp.client

import java.io.ByteArrayInputStream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client
import sttp.model.StatusCode

class RetryWhenDefaultTest extends AnyFlatSpec with Matchers {
  private val simpleRequest = basicRequest.get(uri"http://localhost")

  it should "not retry 200 response" in {
    RetryWhen.Default(simpleRequest, Right(Response.ok(""))) shouldBe false
  }

  it should "retry 500 response" in {
    RetryWhen.Default(simpleRequest, Right(Response("", StatusCode.InternalServerError))) shouldBe true
  }

  it should "retry connection exceptions" in {
    RetryWhen.Default(simpleRequest, Left(new client.SttpClientException.ConnectException(null))) shouldBe true
  }

  it should "not retry read exceptions" in {
    RetryWhen.Default(simpleRequest, Left(new client.SttpClientException.ReadException(null))) shouldBe false
  }

  it should "not retry input stream bodies" in {
    RetryWhen.Default(
      simpleRequest.body(new ByteArrayInputStream(new Array[Byte](8))),
      Right(Response("", StatusCode.InternalServerError))
    ) shouldBe false
  }
}
