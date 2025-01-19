# Migrating to sttp-client4

## Top-level package

The top-level package for sttp-client4 is `sttp.client4`. This means that sttp-client3 and sttp-client4 can be used side-by-side in the same project. They do share sttp-model and sttp-shared libraries, but these did not see a major version change, and are designed to be binary-compatible.

## Request & backend type changes

The `RequestT` type is retired, being replaced by `PartialRequest` and `GenericRequest`.

`PartialRequest` is the type of requests before the method & uri are set. As before, it allows setting the headers and body, creating a reusable base for defining full requests. After the uri & method are set, the request description becomes a `Request`, which can be sent. Additionally, methods to set the body or handle the response as a non-blocking, asynchronous stream become available, as well as converting the request to a web socket one. This yields requests of type `StreamRequests`, `WebSocketRequest` and `WebSocketStreamRequest`. Hence, `GenericRequest` is never used directly in user code.

A parallel change is from a fully-parametrized `SttpBackend` to a family of traits: `SyncBackend`, `Backend`, `StreamBackend`, `WebSocketBackend`, `WebSocketSyncBackend`, `WebSocketStreamBackend`. These specialize the backend type to the capabilities they support, and are used to send requests of the corresponding type.

These changes are introduced to simplify the types, improve error reporting and enhance IDE completions. As a tradeoff, defining generic backend wrappers requires defining constructors for each of the backend subtype.

Moreover, only `request.send(backend)` should be used, instead of `backend.send(request)`; both work, but the first variant is more IDE-friendly, and for the sake of consistency, it's the encouraged one, and the only one used in the documentation.

## Removed implicit `BodySerializer`

All request bodies have now to be explicitly converted to a `BasicBody` or one of the basic types (`String`, `InputStream`, `File`, `Array[Byte]`, `ByteBuffer`, `Map[String, String]`). This change has the highest impact when it comes to JSON bodies, which now have to be set using the `asJson` method.

That is, importing JSON integration (e.g. through `import sttp.client4.jsoniter.*`), brings into scope both `asJson` response handling descriptions, as well as a `asJson(T): BasicBody` methods. A body can be set on a request using `request.body(asJson(...))`.

Previously, the high level type -> `BasicBody` conversion was implicit, and the JSON integrations contained implicit conversions from high-level types to the JSON string (provided the library-specific encoders were in scope). While this design did save some keystrokes, it also provided poor error reporting, and the format to which the body was converted was not always clear when reading the code. This motivated the change to explicitly calling body conversions.

## `...OrFailed` response handling

New response handling descriptions are added, which fail (throw an exception / return a failed effect) if the response is not successful (2xx status code). These include `stringOrFail`, `asByteArrayOrFail`, `asJsonOrFail` etc. Note that this is different from e.g. `asStringAlways`, which always handles the response as a string, regardless of the status code.

Any `Either`-based response description can be converted to a failing one using `.orFail` and `.orFailDeserialization`. This replaces the `.getRight` / `.getEither` extension methods.

## Other changes

* `BackendOptions` replaces `SttpBackendOptions`
* `Backend.monad` replaces `SttpBackend.responseMonad`
* `DefaultSyncBackend` and `DefaultHttpBackend` are provided. They allow limited customization, but are a good entry-level backend for many use-cases.
* `HttpClientBackend` is move to a dedicated package
* `SimpleHttpClient` is removed, `import sttp.client4.quick.*` provides a default backend instance with a no-arg `request.send()` extension method
* documentation & examples use Scala 3
* the `autoDecompressionDisabled` option is superseded by `autoDecompressionEnabled`
* `async-http-client` backends are removed (as there's no reactive streams support in v3, making integration difficult)
* request attributes replace request tags (same mechanism as in Tapir)
* the parametrization of `ResponseException` is simplified, `DeserializationException` does not have a type parameter, always requiring an `Exception` as the cause instead
* when a `ResponseException` is thrown in the response handling specification, this will be logged as a successful response (as the response was received correctly), and counted as a success in the metrics as well
* `HttpError` is renamed to `UnexpectedStatusCode`, and along with `DeserializationException`, both types are nested within `ResponseException`
* the `opentelemetry-metrics-backend` module is renamed to `opentelemetry-backend`