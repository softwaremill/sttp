package sttp.client4

package object ws {
  object async extends SttpWebSocketAsyncApi
  object sync extends SttpWebSocketSyncApi
  object stream extends SttpWebSocketStreamApi
}
