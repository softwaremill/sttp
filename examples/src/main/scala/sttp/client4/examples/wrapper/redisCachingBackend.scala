// {cat=Backend wrapper; effects=Synchronous; backend=HttpClient}: Use the caching backend wrapper with Redis

//> using dep com.softwaremill.sttp.client4::core:4.0.0-M25
//> using dep com.softwaremill.sttp.client4::caching-backend:4.0.0-M25
//> using dep redis.clients:jedis:5.2.0
//> using dep ch.qos.logback:logback-classic:1.5.15

package sttp.client4.examples.wrapper

import redis.clients.jedis.UnifiedJedis
import sttp.client4.*
import sttp.client4.caching.Cache
import sttp.client4.caching.CachingBackend
import sttp.shared.Identity

import scala.concurrent.duration.FiniteDuration

// you'll need to start redis to run this demo, e.g. using Docker:
// docker run -d --name redis-stack -p 6379:6379 -p 8001:8001 redis/redis-stack:latest

class RedisCache(jedis: UnifiedJedis) extends Cache[Identity]:
  override def get(key: Array[Byte]): Option[Array[Byte]] = Option(jedis.get(key))

  override def delete(key: Array[Byte]): Unit =
    val _ = jedis.del(key)

  override def set(key: Array[Byte], value: Array[Byte], ttl: FiniteDuration): Unit =
    val _ = jedis.setex(key, ttl.toSeconds.toInt, value)

  override def close(): Unit = jedis.close()

@main def redisCachingBackend(): Unit =
  val backend: WebSocketSyncBackend =
    CachingBackend(DefaultSyncBackend(), new RedisCache(new UnifiedJedis("redis://localhost:6379")))

  // returns a response with a max-age of 3 seconds
  val request = basicRequest.get(uri"https://httpbin.org/cache/3")

  val response1 = request.send(backend) // the logs should contain information that the response is stored in the cache
  println(s"Response 1: (${response1.code})")
  println(response1.body)

  val response2 = request.send(backend) // an immediate subsequent request should be read from the cache
  println(s"Response 2: (${response2.code})")
  println(response2.body)

  Thread.sleep(5000)
  val response3 = request.send(backend) // after 5 seconds, the cache should be invalidated
  println(s"Response 3: (${response3.code})")
  println(response3.body)
