package sttp.client4.logging

import scala.concurrent.duration.Duration

/** Contains the timings of different parts of the response processing.
  *
  * @param bodyReceived
  *   The time it took from sending the request, to receiving the entire response body.
  * @param bodyProcessed
  *   The time it took from sending the request, to processing the entire response body (e.g. including parsing).
  */
case class ResponseTimings(bodyReceived: Duration, bodyProcessed: Duration)
