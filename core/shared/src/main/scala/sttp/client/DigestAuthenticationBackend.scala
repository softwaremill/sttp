package sttp.client

import java.nio.charset.Charset
import java.security.MessageDigest

import sttp.client.DigestAuthenticationBackend._
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.ws.WebSocketResponse
import sttp.model.HeaderNames

import scala.language.higherKinds
import scala.util.Random
import scala.util.matching.Regex

class DigestAuthenticationBackend[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S, WS_HANDLER])
    extends SttpBackend[F, S, WS_HANDLER] {
  override def send[T](request: Request[T, S]): F[Response[T]] = {
    if (request.tag(DigestAuthTag).isDefined) {
      val digestAuthData = request.tag(DigestAuthTag).get.asInstanceOf[DigestAuthData]
      implicit val m: MonadError[F] = responseMonad
      delegate.send(request).flatMap { response =>
        if (response.code.code == 401) {
          response
            .header(HeaderNames.WwwAuthenticate)
            .map { authHeader =>
              println(authHeader)
              (for {
                realmMatch <- DigestRealmRegex.findFirstMatchIn(authHeader)
                nonceMatch <- NonceRegex.findFirstMatchIn(authHeader)
              } yield {
                val authHeaderValue: String =
                  calculateDigestAuth(request, digestAuthData, authHeader, realmMatch, nonceMatch)
                println("=============")
                authHeaderValue.split(",").foreach(println)
                delegate.send(request.header(HeaderNames.Authorization, authHeaderValue))
              }).getOrElse(response.unit)
            }
            .getOrElse(response.unit)
        } else {
          response.unit
        }
      }
    } else {
      delegate.send(request)
    }
  }

  private def calculateDigestAuth[T](
      request: Request[T, S],
      digestAuthData: DigestAuthData,
      authHeader: String,
      realmMatch: Regex.Match,
      nonceMatch: Regex.Match
  ) = {
    val qualityOfProtection = QopRegex.findFirstMatchIn(authHeader).map(_.group(1))
    val algorithm = AlgorithmRegex.findFirstMatchIn(authHeader).map(_.group(1)).getOrElse("MD5")
    val messageDigest = MessageDigest.getInstance(algorithm)
    val digestUri = "/" + request.uri.pathSegments.map(_.v).mkString("/")
    val clientNonce = generateClientNonce()
    val nonceCount = "00000001"
    val responseChallenge: String =
      calculateResponseChallenge(
        request,
        digestAuthData,
        realmMatch.group(1),
        qualityOfProtection,
        nonceMatch.group(1),
        digestUri,
        clientNonce,
        nonceCount,
        messageDigest,
        algorithm
      )
    val authHeaderValue = createAuthHeaderValue(
      digestAuthData,
      nonceMatch.group(1),
      realmMatch.group(1),
      qualityOfProtection,
      digestUri,
      clientNonce,
      responseChallenge,
      nonceCount,
      algorithm
    )
    authHeaderValue
  }

  private def calculateResponseChallenge[T](
      request: Request[T, S],
      digestAuthData: DigestAuthData,
      realm: String,
      qop: Option[String],
      nonce: String,
      digestUri: String,
      clientNonce: String,
      nonceCount: String,
      messageDigest: MessageDigest,
      algorithm: String
  ) = {
    val ha1 = calculateHa1(digestAuthData, realm, messageDigest, algorithm, nonce, clientNonce)
    val ha2 = calculateHa2(request, qop, digestUri, messageDigest)
    calculateChallange(qop, nonce, clientNonce, nonceCount, messageDigest, ha1, ha2)
  }

  private def calculateHa1[T](
      digestAuthData: DigestAuthData,
      realm: String,
      messageDigest: MessageDigest,
      algorithm: String,
      nonce: String,
      cnonce: String
  ) = {
    val base = md5HexString(s"${digestAuthData.username}:$realm:${digestAuthData.password}", messageDigest)
    if (algorithm.equalsIgnoreCase("MD5-sess")) {
      md5HexString(s"${base}:$nonce:$cnonce", messageDigest)
    } else {
      base
    }
  }

  private def calculateChallange[T](
      qop: Option[String],
      nonce: String,
      clientNonce: String,
      nonceCount: String,
      messageDigest: MessageDigest,
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
      request: Request[T, S],
      qop: Option[String],
      digestUri: String,
      messageDigest: MessageDigest
  ) = {
    qop match {
      case Some(QualityOfProtectionAuth) => md5HexString(s"${request.method.method}:$digestUri", messageDigest)
      case None                          => md5HexString(s"${request.method.method}:$digestUri", messageDigest)
      case Some(QualityOfProtectionAuthInt) =>
        md5HexString(
          s"${request.method.method}:$digestUri:${byteArrayToHexString(messageDigest.digest(request.body match {
            case NoBody                => throw new IllegalStateException("Qop auth-int cannot be used with a non-repeatable entity")
            case StringBody(s, e, dct) => s.getBytes(Charset.forName(e))
          }))}",
          messageDigest
        ) //TODO
    }
  }

  private def generateClientNonce[T]() = {
    val bytes = new Array[Byte](16)
    Random.nextBytes(bytes)
    byteArrayToHexString(bytes)
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
      algorithm: String
  ) = {
    val digestOut = Some(s"""Digest username="${digestAuthData.username}"""")
    val realmOut = Some(s"""realm="$realm"""")
    val uriOut = Some(s"""uri="$digestUri"""")
    val nonceOut = Some(s"""nonce="$nonce"""")
    val qopOut = qop.map(q => s"""qop="$q"""")
    val nc = Some(s"nc=$nonceCount")
    val challengeOut = Some(s"""response="$challenge"""")
    val cnonceOut = Some(s"""cnonce="$clientNonce"""")
    val algorithmOut = Some(s"""algorithm="$algorithm"""")
    val authHeaderValue =
      List(digestOut, realmOut, uriOut, nonceOut, qopOut, challengeOut, cnonceOut, nc, algorithmOut).flatten
        .mkString(", ")
    authHeaderValue
  }

  private def md5HexString(text: String, messageDigest: MessageDigest) = {
    byteArrayToHexString(messageDigest.digest(text.getBytes(Charset.forName("UTF-8"))))
  }

  private def byteArrayToHexString(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = delegate.openWebsocket(request, handler)

  override def close(): F[Unit] = delegate.close()
  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object DigestAuthenticationBackend {
  val DigestAuthTag = "__sttp_DigestAuth"
  val DigestRealmRegex = "Digest realm=\"(.*?)\"".r
  val QopRegex = "qop=\"(.*?)\"".r
  val NonceRegex = "nonce=\"(.*?)\"".r
  val AlgorithmRegex = "algorithm=(.*?),".r
  val QualityOfProtectionAuth = "auth"
  val QualityOfProtectionAuthInt = "auth-int"
  case class DigestAuthData(username: String, password: String)
}
