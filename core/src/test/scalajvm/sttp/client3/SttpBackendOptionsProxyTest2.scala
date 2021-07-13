package sttp.client3

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException
import java.net.URI

class SttpBackendOptionsProxyTest2 extends AnyFlatSpec with Matchers {
  it should "throw UnsupportedOperationException with reason" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      SttpBackendOptions.ProxyType.Http,
      nonProxyHosts = Nil,
      onlyProxyHosts = Nil
    )

    val proxySelector = proxySetting.asJavaProxySelector
    val ex = intercept[UnsupportedOperationException] {
      val uri = new URI("foo")
      val ioe = new IOException("bar")
      proxySelector.connectFailed(uri, proxySetting.inetSocketAddress, ioe)
    }
    ex.getMessage should be("Couldn't connect to the proxy server, uri: foo, socket: fakeproxyserverhost:8080")
  }
}
