package com.softwaremill

import java.io.{ByteArrayOutputStream, InputStream, OutputStream }
import java.nio.ByteBuffer

import com.softwaremill.sttp.internal.SttpFile

import scala.annotation.{implicitNotFound, tailrec}
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.language.higherKinds

package object sttp extends SttpApi