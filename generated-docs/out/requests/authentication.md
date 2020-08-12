# Authentication

sttp supports basic, bearer-token based authentication and digest authentication. Two first cases are handled by adding an `Authorization` header with the appropriate credentials.

Basic authentication, using which the username and password are encoded using Base64, can be added as follows:

```scala
import sttp.client._

val username = "mary"
val password = "p@assword"
basicRequest.auth.basic(username, password)
```

A bearer token can be added using:

```scala
val token = "zMDjRfl76ZC9Ub0wnz4XsNiRVBChTYbJcE3F"
basicRequest.auth.bearer(token)
```

## Digest authentication

This type of authentication works differently. In its assumptions it is based on an additional message exchange between client and server. Due to that a special wrapping backend is need to handle that additional logic.

In order to add digest authentication support just wrap other backend as follows:

```scala
val myBackend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
new DigestAuthenticationBackend(myBackend)
```

Then only thing which we need to do is to pass our credentials to the relevant request:

```scala
val secureRequest = basicRequest.auth.digest(username, password)
```

It is also possible to use digest authentication against proxy:

```scala
val secureProxyRequest = basicRequest.proxyAuth.digest(username, password)
```

Both of above methods can be combined with different values if proxy and target server use digest authentication.

To learn more about digest authentication visit [wikipedia](https://en.wikipedia.org/wiki/Digest_access_authentication)

Also keep in mind that there are some limitations with the current implementation:

* there is no caching so each request will result in an additional round-trip (or two in case of proxy and server)
* authorizationInfo is not supported
* scalajs supports only md5 algorithm

## OAuth2

You can use sttp with OAuth2. Looking at the [OAuth2 protocol flow](https://tools.ietf.org/html/rfc6749#section-1.2), sttp might be helpful in the second and third step of the process:

1. (A)/(B) - Your UI needs to enable the user to authenticate. Your application will then receive a callback from the authentication server, which will include an authentication code.

2. (C)/(D) - You need to send a request to the authentication server, passing in the authentication code from step 1. You'll receive an access token in response (and optionally a refresh token). For example, if you were using GitHub as your authentication server, you'd need to take the values of `clientId` and `clientSecret` from the GitHub settings, then take the `authCode` received in step 1 above, and send a request like this:
```scala
import sttp.client.circe._
import io.circe._
import io.circe.generic.semiauto._

val authCode = "SplxlOBeZQQYbYS6WxSbIA"
val clientId = "myClient123"
val clientSecret = "s3cret"
case class MyTokenResponse(access_token: String, scope: String, token_type: String, refresh_token: Option[String])
implicit val tokenResponseDecoder: Decoder[MyTokenResponse] = deriveDecoder[MyTokenResponse]
val backend = HttpURLConnectionBackend()

val tokenRequest = basicRequest
    .post(uri"https://github.com/login/oauth/access_token?code=$authCode&grant_type=authorization_code")
    .auth
    .basic(clientId, clientSecret)
    .header("accept","application/json")
val authResponse = tokenRequest.response(asJson[MyTokenResponse]).send(backend)
val accessToken = authResponse.body.map(_.access_token)
```

3. (E)/(F) - Once you have the access token, you can use it to request the protected resource from the resource server, depending on its specification.
