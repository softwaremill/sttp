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

  it should "work with uri which has both - path and query" in {
    val request = basicRequest
      .get(uri"http://www.google.com/path/to/resource?parameter=value&parameter2=value2")
      .digestAuth("admin", "password")

    val response =
      responseWithAuthorizationHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/path/to/resource?parameter=value&parameter2=value2", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  it should "work with uri which has only path" in {
    val request = basicRequest
      .get(uri"http://www.google.com/path/to/resource")
      .digestAuth("admin", "password")

    val response =
      responseWithAuthorizationHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/path/to/resource", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  it should "work with uri which has only query" in {
    val request = basicRequest
      .get(uri"http://www.google.com/?parameter=value&parameter2=value2")
      .digestAuth("admin", "password")

    val response =
      responseWithAuthorizationHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/parameter=value&parameter2=value2", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  it should "work with multiple wwwAuthHeaders" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .digestAuth("admin", "password")

    val response =
      responseWithHeaders(
        List(
          Header.notValidated(
            HeaderNames.WwwAuthenticate,
            """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth""""
          ),
          Header.notValidated(
            HeaderNames.WwwAuthenticate,
            """Basic realm="myrealm""""
          )
        ).reverse
      )
    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  it should "not retry when password was wrong" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header.notValidated(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="BBBBBB", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val response =
      responseWithAuthorizationHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")

    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header shouldBe empty
  }

  it should "not retry when nonce is different" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header.notValidated(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val response =
      responseWithAuthorizationHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")

    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header shouldBe empty
  }

  it should "retry when nonce is different but was stale" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header.notValidated(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val response =
      responseWithAuthorizationHeader(
        """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth", stale=true"""
      )

    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  it should "retry when nonce is different but was stale (and quoted)" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header.notValidated(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val response =
      responseWithAuthorizationHeader(
        """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth", stale="true""""
      )

    val header = new DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  private def responseWithAuthorizationHeader(headerValue: String) = {
    responseWithHeaders(
      List(
        Header.notValidated(
          HeaderNames.WwwAuthenticate,
          headerValue
        )
      )
    )
  }

  private def responseWithHeaders(headers: List[Header]) = {
    Response[Either[String, String]](
      Right(""),
      StatusCode.Unauthorized,
      "Unauthorized",
      headers,
      List.empty
    )
  }
}
