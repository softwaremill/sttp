package com.softwaremill.sttp

import java.net.InetSocketAddress

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
  case class Proxy(host: String, port: Int, proxyType: ProxyType) {
    def asJava = new java.net.Proxy(proxyType.asJava, inetSocketAddress)
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
    Empty.connectionTimeout(ct)

  def httpProxy(host: String, port: Int): SttpBackendOptions =
    Empty.httpProxy(host, port)

  def socksProxy(host: String, port: Int): SttpBackendOptions =
    Empty.socksProxy(host, port)

  private def loadSystemProxy: Option[Proxy] = {
    def system(hostProp: String,
               portProp: String,
               make: (String, Int) => Proxy,
               defaultPort: Int) = {
      val host = Option(System.getProperty(hostProp))
      def port = Try(System.getProperty(portProp).toInt).getOrElse(defaultPort)
      host.map(make(_, port))
    }

    def proxy(t: ProxyType)(host: String, port: Int) = Proxy(host, port, t)

    import ProxyType._
    val socks = system("socksProxyHost", "socksProxyPort", proxy(Socks), 1080)
    val http = system("http.proxyHost", "http.proxyPort", proxy(Http), 80)

    Seq(socks, http).find(_.isDefined).flatten
  }

}
