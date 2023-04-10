# 3. Separate backend types

Date: 2023-02-22

## Context

Sttp base types are [being refactored](https://github.com/softwaremill/sttp/pull/1703) to decrease the learning curve, 
and generally improve the developer experience.

## Decision

The `SttpBackend` type is split into a type hierarchy, with new subtypes representing backends with different 
effects and capabilities.

The root of the hierarchy is `GenericBackend`, which corresponds to the old backend: it is parametrised with the
type constructor used to represent effects, and a set of capabilities that the backend supports. Additionally, there's
a number of subtypes: `Backend`, `SyncBackend`, `StreamBackend`, `WebSocketBackend` and `WebSocketStreamBackend` which
fix the capabilities, and in case of `SyncBackend` also the effect type.

### What do we gain

Thanks to the different kinds of backends being represented by top-level types, the `send` methods on request 
descriptions can now have more developer-friendly and precise signatures. For example:

```scala
class Request[T] {
  def send[F[_]](backend: Backend[F]): F[Response[T]] = backend.send(this)
  def send(backend: SyncBackend): Response[T] = backend.send(this)
}
```

Specifically, for a request sent using a synchronous request, the result type is a `Response` directly, without
the `Identity` wrapper. This improves ergonomics (e.g. when using autocomplete) and type inference.

Moreover:
* users are exposed to simpler types, such as `SyncBackend` instead of `SttpBackend[Identity, Any]`
* error messages are more precise, pointing to the specific backend type that is required to send a request

### What do we loose

Backend wrappers, which are designed to work with any delegate backend, now need to have 5 alternate constructors,
for each specific backend subtype. For example:

```scala
object FollowRedirectsBackend {
  def apply(delegate: SyncBackend): SyncBackend = new FollowRedirectsBackend(delegate) with SyncBackend {}
  def apply[F[_]](delegate: Backend[F]): Backend[F] = new FollowRedirectsBackend(delegate) with Backend[F] {}
  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] = new FollowRedirectsBackend(delegate) with WebSocketBackend[F] {}
  def apply[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] = new FollowRedirectsBackend(delegate) with StreamBackend[F, S] {}
  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] = new FollowRedirectsBackend(delegate) with WebSocketStreamBackend[F, S] {}
}
```

Additionally, adding more capabilities will require enriching the type hierarchy with more subtypes, as well as adding
new variants to any backend wrappers. However, in the course of sttp's history, no new capabilities have been added, and
we do not foresee having to add more in the future.

### Alternate designs

We [have explored](https://github.com/adpi2/sttp/pull/5) an alternate design, where the `GenericBackend` was a 
stand-alone type, to which types representing to specific capabilities (`Backend`, `StreamBackend` etc.) delegate. 
This partially replaced the inheritance with composition. 

The main goal of the change was to allow implementing generic backend wrappers in a more straightforward way. The
delegating types included a `SelfType` type parameter, which was leveraged by generic backend wrappers, e.g.:

```scala
trait SyncBackend extends Backend[Identity] { self =>
  override type Capabilities <: Any
  override type SelfType <: SyncBackend
}

object FollowRedirectsBackend {
  def apply[F[_]](delegate: Backend[F]): delegate.SelfType =
    delegate.wrap(new FollowRedirectsBackend(_, FollowRedirectsConfig.Default))
}
```

However, this had two major drawbacks:

* the type inference on the wrapped backends was worse, as it returned e.g. `SyncBackend.SelfType` instead of
  `SyncBackend`
* alternatively, we could fix the self type to become type aliases instead of type bounds, but then the subtyping 
  relation between the backend types has been lost
* additionally, using two wrappers in a generic way resulted in type such as `delegate.SelfType#SelfType`, which
  are not only not readable, but also rejected by the Scala 3 compiler.