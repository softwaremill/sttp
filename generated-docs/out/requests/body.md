# Body

## Text data

In its simplest form, the request's body can be set as a `String`. By default, this method will:

* use the UTF-8 encoding to convert the string to a byte array
* if not specified before, set `Content-Type: text/plain`
* if not specified before, set `Content-Length` to the number of bytes in the array

A `String` body can be set on a request as follows:

```scala
import sttp.client4.*
basicRequest.body("Hello, world!")
```

It is also possible to use a different character encoding:

```scala
import sttp.client4.*
basicRequest.body("Hello, world!", "utf-8")
```

## Binary data

To set a binary-data body, the following methods are available:

```scala
import sttp.client4.*

val bytes: Array[Byte] = ???
basicRequest.body(bytes)

import java.nio.ByteBuffer
val byteBuffer: ByteBuffer = ???
basicRequest.body(byteBuffer)

import java.io.ByteArrayInputStream
val inputStream: ByteArrayInputStream = ???
basicRequest.body(inputStream)
```

If not specified before, these methods will set the content type to `application/octet-stream`. When using a byte array, additionally the content length will be set to the length of the array (unless specified explicitly).

```{eval-rst}
.. note::

  While the object defining a request is immutable, setting a mutable request body will make the whole request definition mutable as well. With ``InputStream``, the request can be moreover sent only once, as input streams can be consumed once.
```

## Uploading files

To upload a file, simply set the request body as a `File` or `Path`:

```scala
import sttp.client4.*

import java.io.File
basicRequest.body(new File("data.txt"))

import java.nio.file.Path
basicRequest.body(Path.of("data.txt"))
```

Note that on JavaScript only a `Web/API/File` is allowed.

As with binary body methods, the content type will default to `application/octet-stream`, and the content length will be set to the length of the file (unless specified explicitly).

See also [multi-part](multipart.md) and [streaming](streaming.md) support.

## Form data

If you set the body as a `Map[String, String]` or `Seq[(String, String)]`, it will be encoded as form-data (as if a web form with the given values was submitted). The content type will default to `application/x-www-form-urlencoded`; content length will also be set if not specified.

By default, the `UTF-8` encoding is used, but can be also specified explicitly:

```scala
import sttp.client4.*
basicRequest.body(Map("k1" -> "v1"))
basicRequest.body(Map("k1" -> "v1"), "utf-8")
basicRequest.body("k1" -> "v1", "k2" -> "v2")
basicRequest.body(Seq("k1" -> "v1", "k2" -> "v2"), "utf-8")
```        

## Custom serializers

It is also possible to write custom serializers, which return arbitrary body representations. These should be 
methods/functions which return instances of `BasicBody`, which is a wrapper for one of the supported request body 
types: a `String`, byte array, an input stream, etc.

For example, here's how to write a custom serializer for a case class, with serializer-specific default content type:

```scala
import sttp.client4.*
import sttp.model.MediaType
case class Person(name: String, surname: String, age: Int)

// for this example, assuming names/surnames can't contain commas
def serializePerson(p: Person): BasicBody = { 
  val serialized = s"${p.name},${p.surname},${p.age}"
  StringBody(serialized, "UTF-8", MediaType.TextCsv)
}

basicRequest.body(serializePerson(Person("mary", "smith", 67)))
```

See the implementations of the `BasicBody` trait for more options.
