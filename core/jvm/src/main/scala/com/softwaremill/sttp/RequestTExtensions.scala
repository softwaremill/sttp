package com.softwaremill.sttp

import java.io.File
import java.nio.file.Path

import com.softwaremill.sttp.internal.SttpFile
import scala.collection.immutable.Seq
import scala.language.higherKinds

trait RequestTExtensions[U[_], T, +S] { self: RequestT[U, T, S] =>

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given file.
    */
  def body(file: File): RequestT[U, T, S] = body(SttpFile.fromFile(file))

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given file.
    */
  def body(path: Path): RequestT[U, T, S] = body(SttpFile.fromPath(path))

  def cookie(nv: (String, String)): RequestT[U, T, S] = cookies(nv)
  def cookie(n: String, v: String): RequestT[U, T, S] = cookies((n, v))
  def cookies(r: Response[_]): RequestT[U, T, S] =
    cookies(r.cookies.map(c => (c.name, c.value)): _*)
  def cookies(cs: Seq[Cookie]): RequestT[U, T, S] =
    cookies(cs.map(c => (c.name, c.value)): _*)
  def cookies(nvs: (String, String)*): RequestT[U, T, S] =
    header(CookieHeader, nvs.map(p => p._1 + "=" + p._2).mkString("; "))
}
