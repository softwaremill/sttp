package com.softwaremill.sttp

import java.io.IOException
import java.net.{InetSocketAddress, SocketAddress, URI}
import java.{net, util}

import com.softwaremill.sttp.SttpBackendOptions._

import scala.concurrent.duration._
import scala.util.Try

case class SttpBackendOptions(
    connectionTimeout: FiniteDuration,
    proxy: Option[Proxy]
) {

  def connectionTimeout(ct: FiniteDuration): SttpBackendOptions =
    this.copy(connectionTimeout = ct)
  def httpProxy(host: String, port: Int): SttpBackendOptions =
    this.copy(proxy = Some(Proxy(host, port, ProxyType.Http)))
  def socksProxy(host: String, port: Int): SttpBackendOptions =
    this.copy(proxy = Some(Proxy(host, port, ProxyType.Socks)))
}

object SttpBackendOptions {
  case class Proxy(host: String, port: Int, proxyType: ProxyType, nonProxyHosts: List[String] = Nil) {
    def asJavaProxySelector: net.ProxySelector = new net.ProxySelector {
      override def select(uri: URI): util.List[net.Proxy] = {
        val proxyList = new util.ArrayList[net.Proxy](1)
        val uriHost = uri.getHost
        if (uriHost.startsWith("127.0.0.1") || nonProxyHosts.forall(uriHost.startsWith)) {
          proxyList.add(net.Proxy.NO_PROXY)
        } else {
          proxyList.add(asJavaProxy)
        }
        proxyList
      }

      override def connectFailed(uri: URI, sa: SocketAddress, ioe: IOException): Unit = {
        throw new UnsupportedOperationException("Couldn't connect to the proxy server.")
      }
    }
    def asJavaProxy = new java.net.Proxy(proxyType.asJava, inetSocketAddress)
    def inetSocketAddress: InetSocketAddress =
      InetSocketAddress.createUnresolved(host, port)
  }

  sealed trait ProxyType {
    def asJava: java.net.Proxy.Type
  }

  object ProxyType {
    case object Http extends ProxyType {
      override def asJava: java.net.Proxy.Type = java.net.Proxy.Type.HTTP
    }
    case object Socks extends ProxyType {
      override def asJava: java.net.Proxy.Type = java.net.Proxy.Type.SOCKS
    }
  }

  private val Empty: SttpBackendOptions =
    SttpBackendOptions(30.seconds, None)

  val Default: SttpBackendOptions =
    Empty.copy(proxy = loadSystemProxy)

  def connectionTimeout(ct: FiniteDuration): SttpBackendOptions =
    Default.connectionTimeout(ct)

  def httpProxy(host: String, port: Int): SttpBackendOptions =
    Empty.httpProxy(host, port)

  def socksProxy(host: String, port: Int): SttpBackendOptions =
    Empty.socksProxy(host, port)

  private def loadSystemProxy: Option[Proxy] = {
    def system(hostProp: String, portProp: String, nonProxyHostsPropOption: Option[String], make: (String, Int, List[String]) => Proxy, defaultPort: Int) = {
      val host = Option(System.getProperty(hostProp))
      def port = Try(System.getProperty(portProp).toInt).getOrElse(defaultPort)
      def nonProxyHosts: List[String] = nonProxyHostsPropOption
        .flatMap(nonProxyHostsProp => Try(System.getProperty(nonProxyHostsProp).split("|").toList).toOption)
        .getOrElse(Nil)
      host.map(make(_, port, nonProxyHosts))
    }

    def proxy(t: ProxyType)(host: String, port: Int, nonProxyHosts: List[String]) = Proxy(host, port, t, nonProxyHosts)

    import ProxyType._
    val socks = system("socksProxyHost", "socksProxyPort", None, proxy(Socks), 1080)
    val http = system("http.proxyHost", "http.proxyPort", Some("http.nonProxyHosts"), proxy(Http), 80)
    val https = system("https.proxyHost", "https.proxyPort", Some("http.nonProxyHosts"), proxy(Http), 443)

    Seq(socks, http, https).find(_.isDefined).flatten
  }

}
