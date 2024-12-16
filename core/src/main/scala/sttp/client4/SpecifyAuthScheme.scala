package sttp.client4

import sttp.client4.internal.DigestAuthenticator
import sttp.client4.internal.Utf8
import java.util.Base64
import sttp.attributes.AttributeKey

class SpecifyAuthScheme[+R <: PartialRequestBuilder[R, _]](
    hn: String,
    req: R,
    digestAttributeKey: AttributeKey[DigestAuthenticator.DigestAuthData]
) {
  def basic(user: String, password: String): R = {
    val c = new String(Base64.getEncoder.encode(s"$user:$password".getBytes(Utf8)), Utf8)
    req.header(hn, s"Basic $c")
  }

  def basicToken(token: String): R =
    req.header(hn, s"Basic $token")

  def bearer(token: String): R =
    req.header(hn, s"Bearer $token")

  def digest(user: String, password: String): R =
    req.attribute(digestAttributeKey, DigestAuthenticator.DigestAuthData(user, password))
}
