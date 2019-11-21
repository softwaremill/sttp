package sttp.client.internal

import java.nio.charset.Charset

import sttp.client._
import sttp.client.internal.DigestAuthenticator._
import sttp.model.{Header, HeaderNames, StatusCode}

import scala.language.higherKinds
import scala.util.Random

private[client] class DigestAuthenticator private (
    digestAuthData: DigestAuthData,
    requestHeaderName: String,
    responseHeaderName: String,
    clientNonceGenerator: () => String
) {
  def authenticate[T, _](request: Request[T, _], response: Response[T]): Option[Header] = {
    responseHeaderValue(response.headers(requestHeaderName), request, response.code)
      .map(Header.notValidated(responseHeaderName, _))
  }

  private def responseHeaderValue(
      authHeaderValues: Seq[String],
      request: Request[_, _],
      statusCode: StatusCode
  ): Option[String] = {
    val wwwAuthRawHeaders = authHeaderValues
    wwwAuthRawHeaders.find(_.contains("Digest")).flatMap { inputHeader =>
      if (statusCode == StatusCode.Unauthorized) {
        val parsed = WwwAuthHeaderParser.parse(inputHeader)
        responseHeaderValue(
          request,
          digestAuthData,
          parsed,
          parsed.realm.getOrElse(throw new IllegalArgumentException("Missing realm")),
          parsed.nonce.getOrElse(throw new IllegalArgumentException("Missing nonce"))
        )
      } else {
        None
      }
    }
  }

  private def responseHeaderValue[T](
      request: Request[T, _],
      digestAuthData: DigestAuthData,
      wwwAuthHeader: WwwAuthHeaderValue,
      realmMatch: String,
      nonceMatch: String
  ): Option[String] = {
    val isFirstOrShouldRetry =
      if (request.headers
            .find(_.name.equalsIgnoreCase(HeaderNames.Authorization))
            .exists(_.value.contains("Digest"))) {
        wwwAuthHeader.isStale.getOrElse(false)
      } else {
        true
      }
    if (isFirstOrShouldRetry) {
      val qualityOfProtection = wwwAuthHeader.qop
      val algorithm = wwwAuthHeader.algorithm.getOrElse("MD5")
      val messageDigest = new MessageDigestCompatibility(algorithm)
      val digestUri =
        (Option(request.uri.toJavaUri.getPath), Option(request.uri.toJavaUri.getQuery)) match {
          case (Some(p), Some(q)) if p.trim.nonEmpty && q.trim.nonEmpty => p + q
          case (Some(p), None) if p.trim.nonEmpty                       => p
          case (None, Some(q)) if q.trim.nonEmpty                       => q
          case _                                                        => "/"
        }

      val clientNonce = clientNonceGenerator()
      val nonceCount = "00000001"
      val responseChallenge: String =
        calculateResponseChallenge(
          request,
          digestAuthData,
          realmMatch,
          qualityOfProtection,
          nonceMatch,
          digestUri,
          clientNonce,
          nonceCount,
          messageDigest,
          algorithm
        )
      val authHeaderValue = createAuthHeaderValue(
        digestAuthData,
        nonceMatch,
        realmMatch,
        qualityOfProtection,
        digestUri,
        clientNonce,
        responseChallenge,
        nonceCount,
        algorithm,
        wwwAuthHeader.opaque
      )
      Some(authHeaderValue)
    } else {
      None
    }
  }

  private def calculateResponseChallenge[T](
      request: Request[T, _],
      digestAuthData: DigestAuthData,
      realm: String,
      qop: Option[String],
      nonce: String,
      digestUri: String,
      clientNonce: String,
      nonceCount: String,
      messageDigest: MessageDigestCompatibility,
      algorithm: String
  ) = {
    val ha1 = calculateHa1(digestAuthData, realm, messageDigest, algorithm, nonce, clientNonce)
    val ha2 = calculateHa2(request, qop, digestUri, messageDigest)
    calculateChallenge(qop, nonce, clientNonce, nonceCount, messageDigest, ha1, ha2)
  }

  private def calculateHa1[T](
      digestAuthData: DigestAuthData,
      realm: String,
      messageDigest: MessageDigestCompatibility,
      algorithm: String,
      nonce: String,
      cnonce: String
  ) = {
    val base = md5HexString(s"${digestAuthData.username}:$realm:${digestAuthData.password}", messageDigest)
    if (algorithm.equalsIgnoreCase("MD5-sess")) {
      md5HexString(s"$base:$nonce:$cnonce", messageDigest)
    } else {
      base
    }
  }

  private def calculateChallenge[T](
      qop: Option[String],
      nonce: String,
      clientNonce: String,
      nonceCount: String,
      messageDigest: MessageDigestCompatibility,
      ha1: String,
      ha2: String
  ) = {
    qop match {
      case Some(v) if v == QualityOfProtectionAuth || v == QualityOfProtectionAuthInt =>
        md5HexString(s"$ha1:$nonce:$nonceCount:$clientNonce:$v:$ha2", messageDigest)
      case None => md5HexString(s"$ha1:$nonce:$ha2", messageDigest)
    }
  }

  private def calculateHa2[T](
      request: Request[T, _],
      qop: Option[String],
      digestUri: String,
      messageDigest: MessageDigestCompatibility
  ) = {
    qop match {
      case Some(QualityOfProtectionAuth) => md5HexString(s"${request.method.method}:$digestUri", messageDigest)
      case None                          => md5HexString(s"${request.method.method}:$digestUri", messageDigest)
      case Some(QualityOfProtectionAuthInt) =>
        val body = request.body match {
          case brb: BasicRequestBody =>
            brb match {
              case StringBody(s, e, _)   => s.getBytes(Charset.forName(e))
              case ByteArrayBody(b, _)   => b
              case ByteBufferBody(b, _)  => b.array()
              case InputStreamBody(b, _) => toByteArray(b)
              case _: FileBody           => throw new IllegalStateException("Qop auth-int cannot be used with a file body")
            }
          case _ => throw new IllegalStateException("Qop auth-int cannot be used with a non-basic body")
        }
        md5HexString(
          s"${request.method.method}:$digestUri:${byteArrayToHexString(messageDigest.digest(body))}",
          messageDigest
        )
    }
  }

  private def createAuthHeaderValue[T](
      digestAuthData: DigestAuthData,
      nonce: String,
      realm: String,
      qop: Option[String],
      digestUri: String,
      clientNonce: String,
      challenge: String,
      nonceCount: String,
      algorithm: String,
      opaque: Option[String]
  ) = {
    val digestOut = Some(s"""Digest username="${digestAuthData.username}"""")
    val realmOut = Some(s"""realm="$realm"""")
    val uriOut = Some(s"""uri="$digestUri"""")
    val nonceOut = Some(s"""nonce="$nonce"""")
    val qopOut = qop.map(q => s"""qop=$q""")
    val nc = Some(s"nc=$nonceCount")
    val challengeOut = Some(s"""response="$challenge"""")
    val cnonceOut = Some(s"""cnonce="$clientNonce"""")
    val algorithmOut = Some(s"""algorithm=$algorithm""")
    val opaqueOut = opaque.map(op => s"""opaque="$op"""")
    val authHeaderValue =
      List(digestOut, realmOut, uriOut, nonceOut, qopOut, challengeOut, cnonceOut, nc, algorithmOut, opaqueOut).flatten
        .mkString(", ")
    authHeaderValue
  }
}

private[client] object DigestAuthenticator {
  val QualityOfProtectionAuth = "auth"
  val QualityOfProtectionAuthInt = "auth-int"
  case class DigestAuthData(username: String, password: String)

  private def md5HexString(text: String, messageDigest: MessageDigestCompatibility) = {
    byteArrayToHexString(messageDigest.digest(text.getBytes(Charset.forName("UTF-8"))))
  }

  private def byteArrayToHexString(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

  def defaultClientNonceGenerator(): String = {
    val bytes = new Array[Byte](16)
    Random.nextBytes(bytes)
    byteArrayToHexString(bytes)
  }

  def apply(data: DigestAuthData, clientNonceGenerator: () => String = defaultClientNonceGenerator) =
    new DigestAuthenticator(data, HeaderNames.WwwAuthenticate, HeaderNames.Authorization, clientNonceGenerator)

  def proxy(data: DigestAuthData, clientNonceGenerator: () => String = defaultClientNonceGenerator) =
    new DigestAuthenticator(data, HeaderNames.ProxyAuthenticate, HeaderNames.ProxyAuthorization, clientNonceGenerator)
}
