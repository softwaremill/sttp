package sttp.client3

import org.scalatest.Assertions.assertThrows
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException
import java.net
import java.net.SocketAddress

class SttpBackendOptionsProxyTest extends AnyFlatSpec with Matchers {

  "ignoreProxy" should "return true for a exact match with nonProxyHosts" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      List("a.nonproxy.host", "localhost", "127.*")
    )

    proxySetting.ignoreProxy("a.nonproxy.host") should be(true)
    proxySetting.ignoreProxy("localhost") should be(true)
  }

  it should "return true for wildcard suffix match" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      List("localhost", "127.*")
    )

    proxySetting.ignoreProxy("127.0.0.1") should be(true)
    proxySetting.ignoreProxy("127.1.0.1") should be(true)
  }

  it should "return true for wildcard prefix match" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      List("localhost", "*.local", "127.*")
    )

    proxySetting.ignoreProxy("sttp.local") should be(true)
    proxySetting.ignoreProxy("www.sttp.local") should be(true)
  }

  it should "return false for others" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      List("localhost", "*.local", "127.*")
    )

    proxySetting.ignoreProxy("sttp.local.com") should be(false)
    proxySetting.ignoreProxy("10.127.0.1") should be(false)
  }

  it should "return false for exact onlyProxyHosts match" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      nonProxyHosts = Nil,
      onlyProxyHosts = List("a.nonproxy.host", "localhost", "127.*")
    )

    proxySetting.ignoreProxy("a.nonproxy.host") should be(false)
    proxySetting.ignoreProxy("localhost") should be(false)
  }

  it should "return false for onlyProxyHosts suffix match" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      nonProxyHosts = Nil,
      onlyProxyHosts = List("localhost", "127.*")
    )

    proxySetting.ignoreProxy("127.0.0.1") should be(false)
    proxySetting.ignoreProxy("127.1.0.1") should be(false)
  }

  it should "return false for onlyProxyHosts prefix match" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      nonProxyHosts = Nil,
      onlyProxyHosts = List("localhost", "*.local")
    )

    proxySetting.ignoreProxy("sttp.local") should be(false)
    proxySetting.ignoreProxy("www.sttp.local") should be(false)
  }

  it should "return true if host does not match any host from onlyProxyHosts" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      nonProxyHosts = Nil,
      onlyProxyHosts = List("localhost", "*.local", "127.*")
    )

    proxySetting.ignoreProxy("dorime") should be(true)
    proxySetting.ignoreProxy("interimo.adaptare") should be(true)
  }

  it should "return true if host matches nonProxyHost despite matching onlyProxyHosts" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      nonProxyHosts = List("localhost"),
      onlyProxyHosts = List("localhost")
    )

    proxySetting.ignoreProxy("localhost") should be(true)
  }

  it should "return false if both nonProxyHost and onlyProxyHosts is Nil" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      nonProxyHosts = Nil,
      onlyProxyHosts = Nil
    )

    proxySetting.ignoreProxy("localhost") should be(false)
  }

  it should "throw UnsupportedOperationException with reason" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      nonProxyHosts = Nil,
      onlyProxyHosts = Nil
    )
    val uri = new net.URI("foo")
    val localAddress = new net.InetSocketAddress(8888)
    val ioe = new IOException("bar")

    val proxySelector = proxySetting.asJavaProxySelector
    val ex = intercept[UnsupportedOperationException] {
      proxySelector.connectFailed(uri, localAddress, ioe)
    }
    ex.getMessage should be("Couldn't connect to the proxy server, uri: foo, socket: 0.0.0.0/0.0.0.0:8888, ioe: bar")
  }
}
