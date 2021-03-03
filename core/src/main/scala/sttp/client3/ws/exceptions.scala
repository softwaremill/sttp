package sttp.client3.ws

import sttp.model.StatusCode

class NotAWebSocketException(statusCode: StatusCode)
    extends Exception(s"Not a web socket; got response code: $statusCode")

class GotAWebSocketException() extends Exception("Got a web socket, but expected normal content")
