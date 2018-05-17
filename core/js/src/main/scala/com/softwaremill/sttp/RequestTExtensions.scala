package com.softwaremill.sttp

import scala.language.higherKinds

trait RequestTExtensions[U[_], T, +S] { self: RequestT[U, T, S] =>

}
