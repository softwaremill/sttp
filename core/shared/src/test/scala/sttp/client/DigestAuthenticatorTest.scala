package sttp.client

import org.scalatest.{FreeSpec, Matchers, OptionValues}
import sttp.client.DigestAuthenticator.DigestAuthData
import sttp.model.{Header, HeaderNames, StatusCode}

import scala.util.{Failure, Try}

class DigestAuthenticatorTest extends FreeSpec with Matchers with OptionValues {
  "should work" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .auth
      .digest("admin", "password")

    val response =
      responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.name shouldBe HeaderNames.Authorization
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "throw exception when wwAuth header is invalid (missing nonce)" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .auth
      .digest("admin", "password")

    val response =
      responseWithAuthenticateHeader("""Digest realm="myrealm", algorithm=MD5, qop="auth"""")
    Try(DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)) shouldBe a[
      Failure[IllegalArgumentException]
    ]
  }

  "work with uri which has both - path and query" in {
    val request = basicRequest
      .get(uri"http://www.google.com/path/to/resource?parameter=value&parameter2=value2")
      .auth
      .digest("admin", "password")

    val response =
      responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/path/to/resource?parameter=value&parameter2=value2", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "work with uri which has only path" in {
    val request = basicRequest
      .get(uri"http://www.google.com/path/to/resource")
      .auth
      .digest("admin", "password")

    val response =
      responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/path/to/resource", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "work with uri which has only query" in {
    val request = basicRequest
      .get(uri"http://www.google.com/?parameter=value&parameter2=value2")
      .auth
      .digest("admin", "password")

    val response =
      responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/parameter=value&parameter2=value2", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "work with multiple wwwAuthHeaders" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .auth
      .digest("admin", "password")

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
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "not retry when password was wrong" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header.notValidated(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="BBBBBB", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val response =
      responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")

    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header shouldBe empty
  }

  "not retry when nonce is different" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header.notValidated(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val response =
      responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")

    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header shouldBe empty
  }

  "retry when nonce is different but was stale" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header.notValidated(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val response =
      responseWithAuthenticateHeader(
        """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth", stale=true"""
      )

    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "retry when nonce is different but was stale (and quoted)" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header.notValidated(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val response =
      responseWithAuthenticateHeader(
        """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth", stale="true""""
      )

    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, response)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "proxy" - {
    "should work" in {
      val request = basicRequest
        .get(uri"http://google.com")
        .auth
        .digest("admin", "password")

      val response =
        responseWithHeader(
          """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""",
          HeaderNames.ProxyAuthenticate
        )
      val header = DigestAuthenticator.proxy(DigestAuthData("admin", "password")).authenticate(request, response)
      header.value.name shouldBe HeaderNames.ProxyAuthorization
      header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
    }
  }

  private def responseWithAuthenticateHeader(headerValue: String) = {
    responseWithHeader(headerValue, HeaderNames.WwwAuthenticate)
  }

  private def responseWithHeader(headerValue: String, headerName: String) = {
    responseWithHeaders(
      List(
        Header.notValidated(
          headerName,
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
