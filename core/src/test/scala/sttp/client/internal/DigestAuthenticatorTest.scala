package sttp.client.internal

import org.scalatest.OptionValues
import sttp.client.internal.DigestAuthenticator.DigestAuthData
import sttp.client._
import sttp.model.{Header, HeaderNames, StatusCode}

import scala.util.{Failure, Try}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DigestAuthenticatorTest extends AnyFreeSpec with Matchers with OptionValues {
  "should work" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .auth
      .digest("admin", "password")

    val r = responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    header.value.name shouldBe HeaderNames.Authorization
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "throw exception when wwAuth header is invalid (missing nonce)" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .auth
      .digest("admin", "password")

    val r = responseWithAuthenticateHeader("""Digest realm="myrealm", algorithm=MD5, qop="auth"""")
    Try(DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)) shouldBe a[
      Failure[IllegalArgumentException]
    ]
  }

  "work with uri which has both - path and query" in {
    val request = basicRequest
      .get(uri"http://www.google.com/path/to/resource?parameter=value&parameter2=value2")
      .auth
      .digest("admin", "password")

    val r = responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="\/path\/to\/resource\?parameter=value&parameter2=value2", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "work with uri which has only path" in {
    val request = basicRequest
      .get(uri"http://www.google.com/path/to/resource")
      .auth
      .digest("admin", "password")

    val r = responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/path/to/resource", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "work with uri which has only query" in {
    val request = basicRequest
      .get(uri"http://www.google.com/?parameter=value&parameter2=value2")
      .auth
      .digest("admin", "password")

    val r = responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    val expected =
      """Digest username="admin", realm="myrealm", uri="\/\?parameter=value&parameter2=value2", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
    header.value.value should fullyMatch regex expected
  }

  "work with multiple wwwAuthHeaders" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .auth
      .digest("admin", "password")

    val r = response(
      List(
        Header(
          HeaderNames.WwwAuthenticate,
          """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth""""
        ),
        Header(
          HeaderNames.WwwAuthenticate,
          """Basic realm="myrealm""""
        )
      ).reverse,
      StatusCode.Unauthorized
    )
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "not retry when password was wrong" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="BBBBBB", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val r = responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    header shouldBe empty
  }

  "not retry when nonce is different" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val r = responseWithAuthenticateHeader("""Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""")
    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    header shouldBe empty
  }

  "retry when nonce is different but was stale" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val r = responseWithAuthenticateHeader(
      """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth", stale=true"""
    )

    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "retry when nonce is different but was stale (and quoted)" in {
    val request = basicRequest
      .get(uri"http://google.com")
      .header(
        Header(
          HeaderNames.Authorization,
          """Digest username="admin", realm="myrealm", nonce="AAAAAA", uri="/", response="123456", qop=auth, nc=00000001, cnonce="1234567", algorithm=MD5"""
        )
      )
    val r = responseWithAuthenticateHeader(
      """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth", stale="true""""
    )

    val header = DigestAuthenticator(DigestAuthData("admin", "password")).authenticate(request, r)
    header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
  }

  "proxy" - {
    "should work" in {
      val request = basicRequest
        .get(uri"http://google.com")
        .auth
        .digest("admin", "password")

      val r = response(
        """Digest realm="myrealm", nonce="BBBBBB", algorithm=MD5, qop="auth"""",
        HeaderNames.ProxyAuthenticate,
        StatusCode.ProxyAuthenticationRequired
      )
      val header = DigestAuthenticator.proxy(DigestAuthData("admin", "password")).authenticate(request, r)
      header.value.name shouldBe HeaderNames.ProxyAuthorization
      header.value.value should fullyMatch regex """Digest username="admin", realm="myrealm", uri="/", nonce="BBBBBB", qop=auth, response="[0-9a-f]+", cnonce="[0-9a-f]+", nc=000000\d\d, algorithm=MD5"""
    }
  }

  private def responseWithAuthenticateHeader(headerValue: String) = {
    response(headerValue, HeaderNames.WwwAuthenticate, StatusCode.Unauthorized)
  }

  private def response(
      headerValue: String,
      headerName: String,
      statusCode: StatusCode
  ): Response[Either[String, String]] = {
    response(
      List(
        Header(
          headerName,
          headerValue
        )
      ),
      statusCode
    )
  }

  private def response(headers: List[Header], statusCode: StatusCode): Response[Either[String, String]] = {
    Response[Either[String, String]](
      Right(""),
      statusCode,
      "Unauthorized",
      headers
    )
  }
}
