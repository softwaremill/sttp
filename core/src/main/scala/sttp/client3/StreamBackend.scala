package sttp.client3

trait StreamBackend[F[_], +S] extends Backend[F] with AbstractBackend[F, S]
