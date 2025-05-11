package sttp.client4.armeria

import com.linecorp.armeria.client.{ClientFactory, WebClient, WebClientBuilder}
import com.linecorp.armeria.client.encoding.DecodingClient
import com.linecorp.armeria.client.proxy.ProxyConfig
import sttp.client4.BackendOptions
import sttp.client4.BackendOptions.ProxyType

import java.net.InetSocketAddress

object ArmeriaWebClient {
  private def newClientFactory(options: BackendOptions): ClientFactory = {
    val builder = ClientFactory
      .builder()
      .connectTimeoutMillis(options.connectionTimeout.toMillis)
    options.proxy.fold(builder.build()) { proxy =>
      val host = new InetSocketAddress(proxy.host, proxy.port)
      val config = (proxy.proxyType, proxy.auth) match {
        case (ProxyType.Http, None)        => ProxyConfig.connect(host)
        case (ProxyType.Http, Some(auth))  => ProxyConfig.connect(host, auth.username, auth.password, true)
        case (ProxyType.Socks, None)       => ProxyConfig.socks5(host)
        case (ProxyType.Socks, Some(auth)) => ProxyConfig.socks5(host, auth.username, auth.password)
      }
      builder.proxyConfig(config).build()
    }
  }

  /** Create a new [[WebClient]] which is adjusted for sttp client's needs. */
  def newClient(): WebClient = newClient(identity[WebClientBuilder] _)

  /** Create a new [[WebClient]] which is adjusted for sttp client's needs. */
  def newClient(customizeWebClient: WebClientBuilder => WebClientBuilder): WebClient =
    customizeWebClient(
      WebClient
        .builder()
        .decorator(
          DecodingClient
            .builder()
            .autoFillAcceptEncoding(false)
            .strictContentEncoding(true)
            .newDecorator()
        )
    )
      .build()

  /** Create a new [[WebClient]] which is adjusted for sttp client's needs, and uses the timeouts/proxy specified in
    * `options`.
    */
  def newClient(
      options: BackendOptions,
      customizeWebClient: WebClientBuilder => WebClientBuilder = identity
  ): WebClient =
    customizeWebClient(
      WebClient
        .builder()
        .decorator(
          DecodingClient
            .builder()
            .autoFillAcceptEncoding(false)
            .strictContentEncoding(true)
            .newDecorator()
        )
        .factory(newClientFactory(options))
    )
      .build()
}
