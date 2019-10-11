package sttp.client.ws

import sttp.model.Headers

/**
  * @param headers The headers returned after establishing the websocket.
  * @param result The websocket-handler and backend-specific result value, returned after establishing the websocket.
  */
case class WebSocketResponse[WS_RESULT](headers: Headers, result: WS_RESULT)
