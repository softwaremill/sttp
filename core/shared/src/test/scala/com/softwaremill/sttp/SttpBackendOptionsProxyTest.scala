package com.softwaremill.sttp

import org.scalatest.{FlatSpec, Matchers}

class SttpBackendOptionsProxyTest extends FlatSpec with Matchers {

  "ignoreProxy" should "return true for a exact match with nonProxyHosts" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost", 8080,
      SttpBackendOptions.ProxyType.Http,
      List("a.nonproxy.host", "localhost", "127.*")
    )

    proxySetting.ignoreProxy("a.nonproxy.host") should be(true)
    proxySetting.ignoreProxy("localhost") should be(true)
  }

  it should "return true for wildcard suffix match" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost", 8080,
      SttpBackendOptions.ProxyType.Http,
      List("localhost", "127.*")
    )

    proxySetting.ignoreProxy("127.0.0.1") should be(true)
    proxySetting.ignoreProxy("127.1.0.1") should be(true)
  }

  it should "return true for wildcard prefix match" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost", 8080,
      SttpBackendOptions.ProxyType.Http,
      List("localhost", "*.local", "127.*")
    )

    proxySetting.ignoreProxy("sttp.local") should be(true)
    proxySetting.ignoreProxy("www.sttp.local") should be(true)
  }

  it should "return false for others" in {
    val proxySetting = SttpBackendOptions.Proxy(
      "fakeproxyserverhost", 8080,
      SttpBackendOptions.ProxyType.Http,
      List("localhost", "*.local", "127.*")
    )

    proxySetting.ignoreProxy("sttp.local.com") should be(false)
    proxySetting.ignoreProxy("10.127.0.1") should be(false)
  }
}
