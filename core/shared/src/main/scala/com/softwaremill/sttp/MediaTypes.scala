package com.softwaremill.sttp

// https://www.iana.org/assignments/media-types/media-types.xhtml
trait MediaTypes {
  val Binary = "application/octet-stream"
  val CacheManifest = "text/cache-manifest"
  val Css = "text/css"
  val EventStream = "text/event-stream"
  val Form = "application/x-www-form-urlencoded"
  val Html = "text/html"
  val Javascript = "text/javascript"
  val Json = "application/json"
  val Text = "text/plain"
  val XML = "application/xml"
  val XHTML = "application/xhtml+xml"
}

object MediaTypes extends MediaTypes
