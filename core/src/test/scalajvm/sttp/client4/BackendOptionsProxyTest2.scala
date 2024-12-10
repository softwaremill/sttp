package sttp.client4

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException
import java.net.URI

class BackendOptionsProxyTest2 extends AnyFlatSpec with Matchers {
  it should "throw UnsupportedOperationException with reason" in {
    val proxySetting = BackendOptions.Proxy(
      "fakeproxyserverhost",
      8080,
      BackendOptions.ProxyType.Http,
      nonProxyHosts = Nil,
      onlyProxyHosts = Nil
    )

    val proxySelector = proxySetting.asJavaProxySelector
    val ex = intercept[UnsupportedOperationException] {
      val uri = new URI("foo")
      val ioe = new IOException("bar")
      proxySelector.connectFailed(uri, proxySetting.inetSocketAddress, ioe)
    }
    ex.getMessage should startWith("Couldn't connect to the proxy server, uri: foo, socket: fakeproxyserverhost")
  }
}
