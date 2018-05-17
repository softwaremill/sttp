package com.softwaremill.sttp

import scala.collection.immutable.Seq
import scala.language.higherKinds

trait RequestTExtensions[U[_], T, +S] { self: RequestT[U, T, S] =>

  def cookie(nv: (String, String)): RequestT[U, T, S] = cookies(nv)
  def cookie(n: String, v: String): RequestT[U, T, S] = cookies((n, v))
  def cookies(r: Response[_]): RequestT[U, T, S] =
    cookies(r.cookies.map(c => (c.name, c.value)): _*)
  def cookies(cs: Seq[Cookie]): RequestT[U, T, S] =
    cookies(cs.map(c => (c.name, c.value)): _*)
  def cookies(nvs: (String, String)*): RequestT[U, T, S] =
    header(CookieHeader, nvs.map(p => p._1 + "=" + p._2).mkString("; "))
}
