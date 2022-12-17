**Archived** Natchez now supports tracing `Resource`s and `Stream`s out-of-the-box.

# Bayou [![bayou Scala version support](https://index.scala-lang.org/armanbilge/bayou/bayou/latest-by-scala-version.svg)](https://index.scala-lang.org/armanbilge/bayou/bayou)

An experimental extension of [Natchez](https://github.com/tpolecat/natchez)'s `Trace` that supports tracing of `Resource` and `Stream`.

Bayou adds an additional method to the `Trace` API.
```scala
def spanR(name: String): Resource[F, Unit]
```
This creates a new span, and returns a `Resource` such that all effects run within the scope of the `Resource` are run within that span.

For example, vanilla `span` for a traced effect `F` may be implemented in terms of `spanR`.
```scala
def span[A](name: String)(k: F[A]): F[A] =
  spanR(name).use(_ => k)
```
But now it is also possible to span an entire `Stream`.
```scala
def span[A](name: String)(k: Stream[F, A]): Stream[F, A] =
  Stream.resource(spanR(name)).flatMap(_ => k)
```

Bayou's `Trace` fully interoperates with Natchez: `bayou.Trace` extends `natchez.Trace` and is implemented with `natchez.Span`.

## What's the catch?

Bayou's `Trace` cannot be implemented with `Kleisli`; instead it is implemented for `IO` via `IOLocal`.

In practice, I believe this is not a problem, assuming that you initialize tracing early in your `IOApp`. Although this setup must be in concrete `IO` up until you have an instance of `Trace[IO]`, your application logic from that point on may be written in terms of `F[_]: Trace`.

Furthermore, there are advantages to using `IO` as your traced effect.
- No need to `mapK` between your untraced and traced effects, as they are both `IO`.
- `IOLocal` has better performance than `Kleisli` and furthermore has no performance impact on untraced code.

One shortcoming of `IOLocal`-based tracing is that the root span must be installed upon creation. To mitigate this, Bayou offers an `IOTrace` class with additional methods that allow a different root span to be injected after-the-fact.
```scala
def span[A](span: Span[IO])(k: IO[A]): IO[A]

def spanR(span: Span[IO]): Resource[IO, Unit]
```
