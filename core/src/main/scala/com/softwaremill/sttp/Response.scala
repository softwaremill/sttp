package com.softwaremill.sttp

case class Response[T](status: Int, body: T)
