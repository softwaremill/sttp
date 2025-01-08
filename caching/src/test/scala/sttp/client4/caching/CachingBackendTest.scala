package sttp.client4.caching

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.jsoniter._
import sttp.client4.testing.ResponseStub
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.headers.CacheDirective
import sttp.shared.Identity

import scala.concurrent.duration._

class CachingBackendTest extends AnyFlatSpec with Matchers {

  trait StubCache[F[_]] extends Cache[F] {
    def timePassed(seconds: Int): Unit
  }

  def newInMemoryCache = new StubCache[Identity] {
    var storage = new collection.mutable.HashMap[List[Byte], (Array[Byte], Long)]()
    var now = 0L // how many seconds have passed till the dawn of time

    def timePassed(seconds: Int): Unit = {
      now += seconds
      storage = storage.filter { case (_, (_, ttl)) => ttl >= now }
    }

    override def get(key: Array[Byte]): Option[Array[Byte]] = storage.get(key.toList).map(_._1)

    override def delete(key: Array[Byte]): Unit = {
      val _ = storage.remove(key.toList)
    }

    override def set(key: Array[Byte], value: Array[Byte], ttl: FiniteDuration): Unit = {
      val _ = storage.put(key.toList, (value, now + ttl.toSeconds))
    }

    override def close(): Unit = ()

  }

  it should "cache responses" in {
    // given
    val cache = newInMemoryCache
    var invocationCounter = 0 // how many times the request was "sent" by the delegate backend
    val delegate = DefaultSyncBackend.stub
      .whenRequestMatches(_.uri.toString == "http://example1.org")
      .thenRespond {
        invocationCounter += 1
        ResponseStub
          .ok("response body 1")
          .copy(headers = List(Header(HeaderNames.CacheControl, CacheDirective.MaxAge(5.seconds).toString)))
      }
      .whenRequestMatches(_.uri.toString == "http://example2.org")
      .thenRespond {
        invocationCounter += 1
        ResponseStub.ok("response body 2") // no cache-control header
      }
    val cachingBackend = CachingBackend(delegate, cache)

    val request1 = basicRequest.get(uri"http://example1.org").response(asString)
    val request2 = basicRequest.get(uri"http://example2.org").response(asString)

    // A: initial request
    val responseA = cachingBackend.send(request1)
    invocationCounter shouldBe 1

    // B: request before timeout
    // cache.timePassed(2) // cache should be valid
    val responseB = cachingBackend.send(request1)
    invocationCounter shouldBe 1
    responseA.body shouldBe responseB.body

    // C: request after timeout
    cache.timePassed(7) // cache should be emptied
    val responseC = cachingBackend.send(request1)
    invocationCounter shouldBe 2
    responseC.body shouldBe responseA.body

    // D: request to another endpoint
    cachingBackend.send(request2)
    invocationCounter shouldBe 3

    // E: another request to another endpoint, which shouldn't be cached
    cachingBackend.send(request2)
    invocationCounter shouldBe 4
  }

  case class Data(v1: Int, v2: String, v3: Boolean)
  implicit val dataCOdec: JsonValueCodec[Data] = JsonCodecMaker.make

  it should "deserialize cached responses" in {
    // given
    val cache = newInMemoryCache
    var invocationCounter = 0 // how many times the request was "sent" by the delegate backend
    val delegate = DefaultSyncBackend.stub
      .whenRequestMatches(_.uri.toString == "http://example1.org")
      .thenRespond {
        invocationCounter += 1
        ResponseStub
          .ok("""{"v1": 42, "v2": "foo", "v3": true}""")
          .copy(headers = List(Header(HeaderNames.CacheControl, CacheDirective.MaxAge(5.seconds).toString)))
      }
    val cachingBackend = CachingBackend(delegate, cache)

    val request = basicRequest.get(uri"http://example1.org").response(asJson[Data])

    // A: initial request
    val responseA = cachingBackend.send(request)
    invocationCounter shouldBe 1
    responseA.body shouldBe Right(Data(42, "foo", true))

    // B: repeated request (from cache)
    val responseB = cachingBackend.send(request)
    invocationCounter shouldBe 1
    responseB.body shouldBe Right(Data(42, "foo", true))
  }

  it should "include the vary header values in the cache key" in {
    // given
    val cache = newInMemoryCache
    var invocationCounter = 0 // how many times the request was "sent" by the delegate backend
    val delegate = DefaultSyncBackend.stub
      .whenRequestMatches(_.uri.toString == "http://example1.org")
      .thenRespondF { request =>
        invocationCounter += 1
        ResponseStub
          .ok(s"response body: ${request.header("X-Test").getOrElse("no-x-test")}")
          .copy(headers = List(Header(HeaderNames.CacheControl, CacheDirective.MaxAge(5.seconds).toString)))
      }
    val cachingBackend = CachingBackend(delegate, cache)

    val request1 =
      basicRequest.get(uri"http://example1.org").header(HeaderNames.Vary, "X-Test").header("X-Test", "a-value")
    val request2 =
      basicRequest.get(uri"http://example1.org").header(HeaderNames.Vary, "X-Test").header("X-Test", "b-value")

    // A: request with vary headers, first variant
    val responseA = cachingBackend.send(request1)
    invocationCounter shouldBe 1
    responseA.body shouldBe Right("response body: a-value")

    // B: request with vary headers, second variant
    val responseB = cachingBackend.send(request2)
    invocationCounter shouldBe 2 // different vary header values
    responseB.body shouldBe Right("response body: b-value")

    // C: repeated first variant
    val responseC = cachingBackend.send(request1)
    invocationCounter shouldBe 2
    responseC.body shouldBe Right("response body: a-value")

    // D: repeated second variant
    val responseD = cachingBackend.send(request2)
    invocationCounter shouldBe 2
    responseD.body shouldBe Right("response body: b-value")

    // E: first variant, after some time
    cache.timePassed(10)
    val responseE = cachingBackend.send(request1)
    invocationCounter shouldBe 3
    responseE.body shouldBe Right("response body: a-value")
  }
}
