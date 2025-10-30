package sttp.client4.armeria

import com.linecorp.armeria.client.Endpoint
import com.linecorp.armeria.client.proxy.ProxyType._
import com.linecorp.armeria.client.proxy.{ProxyConfig, ProxyConfigSelector}
import com.linecorp.armeria.common.{HttpHeaders, SessionProtocol}
import sttp.client4.BackendOptions.Proxy

class AuthProxyConfigSelector(proxy: Option[Proxy]) extends ProxyConfigSelector {

  override def select(sp: SessionProtocol, e: Endpoint): ProxyConfig =
    proxy match {
      case None                      => ProxyConfig.direct()
      case Some(p) if p.auth.isEmpty => toProxyConfig(p, sp, e)
      case Some(p)                   => toAuthenticatedProxyConfig(p, sp, e)
    }

  private def toAuthenticatedProxyConfig(proxy: Proxy, sp: SessionProtocol, e: Endpoint): ProxyConfig =
    toProxyConfig(proxy, sp, e).proxyType() match {
      case DIRECT  => ProxyConfig.direct()
      case SOCKS4  => ProxyConfig.socks4(proxy.inetSocketAddress, proxy.auth.get.username)
      case SOCKS5  => ProxyConfig.socks5(proxy.inetSocketAddress, proxy.auth.get.username, proxy.auth.get.password)
      case CONNECT =>
        ProxyConfig.connect(
          proxy.inetSocketAddress,
          proxy.auth.get.username,
          proxy.auth.get.password,
          HttpHeaders.of(),
          false
        )
      case HAPROXY => ProxyConfig.haproxy(proxy.inetSocketAddress)
    }

  private def toProxyConfig(proxy: Proxy, sp: SessionProtocol, e: Endpoint) =
    ProxyConfigSelector.of(proxy.asJavaProxySelector).select(sp, e)
}
