.. _requestbody:

Setting the request body
========================

Text data
---------

In its simplest form, the request's body can be set as a ``String``. By default, this method will:

* use the UTF-8 encoding to convert the string to a byte array
* if not specified before, set ``Content-Type: text/plain``
* if not specified before, set ``Content-Length`` to the number of bytes in the array

A ``String`` body can be set on a request as follows::

  sttp.body("Hello, world!")

It is also possible to use a different character encoding::

  def body(b: String)
  def body(b: String, encoding: String)

Binary data
-----------

To set a binary-data body, the following methods are available::

  def body(b: Array[Byte])
  def body(b: ByteBuffer)
  def body(b: InputStream)

If not specified before, these methods will set the content type to ``application/octet-stream``. When using a byte array, additionally the content length will be set to the length of the array (unless specified explicitly).

.. note::

  While the object defining a request is immutable, setting a mutable request body will make the whole request definition mutable as well. With ``InputStream``, the request can be moreover sent only once, as input streams can be consumed once.

Uploading files
---------------

To upload a file, simply set the request body as a ``File``::

  def body(f: File)

File can be:
  * either a ``java.io.File`` or ``java.nio.file.Path`` on the JVM
  * a ``Web/API/File`` on JavaScript

As with binary body methods, the content type will default to ``application/octet-stream``, and the content length will be set to the length of the file (unless specified explicitly).

See also :ref:`multi-part <multipart>` and :ref:`streaming <streaming>` support.

Form data
---------

If you set the body as a ``Map[String, String]`` or ``Seq[(String, String)]``, it will be encoded as form-data (as if a web form with the given values was submitted). The content type will default to ``application/x-www-form-urlencoded``; content length will also be set if not specified.

By default, the ``UTF-8`` encoding is used, but can be also specified explicitly::

  def body(fs: Map[String, String])
  def body(fs: Map[String, String], encoding: String)
  def body(fs: (String, String)*)
  def body(fs: Seq[(String, String)], encoding: String)

.. _requestbody_custom:

Custom body serializers
-----------------------

It is also possible to set custom types as request bodies, as long as there's an implicit ``BodySerializer[B]`` value in scope, which is simply an alias for a function::

  type BodySerializer[B] = B => BasicRequestBody

A ``BasicRequestBody`` is a wrapper for one of the supported request body types: a ``String``/byte array or an input stream.

For example, here's how to write a custom serializer for a case class, with serializer-specific default content type::

  case class Person(name: String, surname: String, age: Int)

  // for this example, assuming names/surnames can't contain commas
  implicit val personSerializer: BodySerializer[Person] = { p: Person =>
    val serialized = s"${p.name},${p.surname},${p.age}"
    StringBody(serialized, "UTF-8", Some("application/csv"))
  }

  sttp.body(Person("mary", "smith", 67))

See the implementations of the ``BasicRequestBody`` trait for more options.

