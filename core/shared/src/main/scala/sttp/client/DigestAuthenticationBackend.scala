package sttp.client

import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Base64

import sttp.client.DigestAuthenticationBackend._
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.monad.syntax._
import sttp.model.{HeaderNames, Headers}

import scala.language.higherKinds
import scala.util.Random
import scala.util.matching.Regex

class DigestAuthenticationBackend[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S, WS_HANDLER])
    extends SttpBackend[F, S, WS_HANDLER] {
  private val md5 = MessageDigest.getInstance("MD5")

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
                val qualityOfProtection = QopRegex.findFirstMatchIn(authHeader).map(_.group(1))
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
                    nonceCount
                  )
                val authHeaderValue = createAuthHeaderValue(
                  digestAuthData,
                  nonceMatch.group(1),
                  realmMatch.group(1),
                  qualityOfProtection,
                  digestUri,
                  clientNonce,
                  responseChallenge,
                  nonceCount
                )
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

  private def calculateResponseChallenge[T](
      request: Request[T, S],
      digestAuthData: DigestAuthData,
      realm: String,
      qop: Option[String],
      nonce: String,
      digestUri: String,
      clientNonce: String,
      nonceCount: String
  ) = {
    val ha1 = md5HexString(s"${digestAuthData.username}:$realm:${digestAuthData.password}")
    val ha2 = qop match {
      case Some(QualityOfProtectionAuth) => md5HexString(s"${request.method.method}:$digestUri")
      case None                          => md5HexString(s"${request.method.method}:$digestUri")
      case Some(QualityOfProtectionAuthInt) =>
        md5HexString(s"${request.method.method}:$digestUri:${md5HexString("body")}") //TODO
    }
    val challenge = qop match {
      case Some(v) if v == QualityOfProtectionAuth || v == QualityOfProtectionAuthInt =>
        md5HexString(s"$ha1:$nonce:$nonceCount:$clientNonce:$v:$ha2")
      case None => md5HexString(s"$ha1:$nonce:$ha2")
    }
    challenge
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
      nonceCount: String
  ) = {
    val digestOut = Some(s"""Digest username="${digestAuthData.username}"""")
    val realmOut = Some(s"""realm="$realm"""")
    val uriOut = Some(s"""uri="$digestUri"""")
    val nonceOut = Some(s"""nonce="$nonce"""")
    val qopOut = qop.map(q => s"""qop="$q"""")
    val nc = Some(s"nc=$nonceCount")
    val challengeOut = Some(s"""response="$challenge"""")
    val cnonceOut = Some(s"""cnonce="$clientNonce"""")
    val authHeaderValue =
      List(digestOut, realmOut, uriOut, nonceOut, qopOut, challengeOut, cnonceOut, nc).flatten.mkString(", ")
    authHeaderValue
  }

  private def md5HexString(text: String) = {
    byteArrayToHexString(md5.digest(text.getBytes(Charset.forName("UTF-8"))))
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
  val QualityOfProtectionAuth = "auth"
  val QualityOfProtectionAuthInt = "auth-int"
  case class DigestAuthData(username: String, password: String)
}
