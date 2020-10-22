# Multipart requests 

To set a multipart body on a request, the `multipartBody` method should be used (instead of `body`). Each body part is represented as an instance of `Part[BasicRequestBody]`, which can be conveniently constructed using `multipart` methods coming from the `sttp.client3` package.

A single part of a multipart request consist of a mandatory name and a payload of type:

* `String`
* `Array[Byte]`
* `ByteBuffer`
* `InputStream`
* `Map[String, String]`
* `Seq[(String, String)]`

To add a file part, the `multipartFile` method (also from the `com.softwaremill.sttp` package) should be used. This method is overloaded and supports `File`/`Path` objects on the JVM, and `Web/API/File` on JS.

The content type of each part is by default the same as when setting simple bodies: `text/plain` for parts of type `String`, `application/x-www-form-urlencoded` for parts of key-value pairs (form data) and `application/octet-stream` otherwise (for binary data).

The parts can be specified using either a `Seq[Multipart]` or by using multiple arguments:

```scala
import sttp.client3._

basicRequest.multipartBody(Seq(multipart("p1", "v1"), multipart("p2", "v2")))
basicRequest.multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
```        

For example:

```scala
import sttp.client3._
import java.io._

val someFile = new File("/sample/path")

basicRequest.multipartBody(
  multipart("text_part", "data1"),
  multipartFile("file_part", someFile), // someFile: File
  multipart("form_part", Map("x" -> "10", "y" -> "yes"))
)
```

## Customising part meta-data

For each part, an optional filename can be specified, as well as a custom content type and additional headers. For example:

```scala
import sttp.client3._
import java.io._

val logoFile = new File("/sample/path/logo123.jpg")
val docFile = new File("/sample/path/doc123.doc")
basicRequest.multipartBody(
  multipartFile("logo", logoFile).fileName("logo.jpg").contentType("image/jpg"),
  multipartFile("text", docFile).fileName("text.doc")
)
```
