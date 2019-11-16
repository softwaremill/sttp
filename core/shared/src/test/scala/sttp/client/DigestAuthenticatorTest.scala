package sttp.client

import org.scalatest.{FlatSpec, Matchers, OptionValues}
import sttp.client.DigestAuthenticationBackend._
import sttp.client.DigestAuthenticator.DigestAuthData
import sttp.model.{Header, HeaderNames, StatusCode}

import scala.util.{Failure, Try}

class DigestAuthenticatorTest extends FlatSpec with Matchers with OptionValues {
  it should "work" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .digestAuth("admin", "password")

    val response =
      responseWithAuthorizationHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  it should "throw exception when wwAuth header is invalid (missing nonce)" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .digestAuth("admin", "password")

    val response =
      responseWithAuthorizationHeader("""Digest realm="myrealm", algorithm=MD5, qop="auth"""")
    Try(new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)) shouldBe a[
      Failure[IllegalArgumentException]
    ]
  }

  it should "use uri path and parameters" in {
    val request = basicRequest
      .get(uri"http://www.google.com/path/to/resource?parameter=value&parameter2=value2")
      .digestAuth("admin", "password")

    val response =
      responseWithAuthorizationHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/path/to/resource?parameter=value&parameter2=value2", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  private def responseWithAuthorizationHeader(headerValue: String) = {
    Response[Either[String, String]](
      Right(""),
      StatusCode.Unauthorized,
      "Unauthorized",
      List(
        Header.notValidated(
          HeaderNames.WwwAuthenticate,
          headerValue
        )
      ),
      List.empty
    )
  }
}
