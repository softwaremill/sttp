package com.softwaremill.sttp

import java.net.InetSocketAddress

import scala.concurrent.duration._
import com.softwaremill.sttp.SttpBackendOptions._

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

  val Default: SttpBackendOptions = SttpBackendOptions(30.seconds, None)

  def connectionTimeout(ct: FiniteDuration): SttpBackendOptions = Default.connectionTimeout(ct)
  def httpProxy(host: String, port: Int): SttpBackendOptions = Default.httpProxy(host, port)
  def socksProxy(host: String, port: Int): SttpBackendOptions = Default.socksProxy(host, port)
}
