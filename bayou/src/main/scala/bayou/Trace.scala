/*
 * Copyright 2022 Arman Bilge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bayou

import cats.Applicative
import cats.data.Kleisli
import cats.effect.IO
import cats.effect.IOLocal
import cats.effect.MonadCancelThrow
import cats.effect.kernel.Resource
import cats.syntax.all._
import natchez.Kernel
import natchez.Span
import natchez.TraceValue

import java.net.URI

trait Trace[F[_]] extends natchez.Trace[F] {

  /**
   * Create a new span, encapsulated as a resource
   */
  def spanR(name: String): Resource[F, Unit]

}

object Trace {
  def apply[F[_]](implicit ev: Trace[F]): ev.type = ev

  /**
   * A `Trace` instance that uses `IOLocal` internally.
   */
  def ioTrace(rootSpan: Span[IO]): IO[Trace[IO]] =
    IOLocal(rootSpan).map { local =>
      new Trace[IO] {

        def put(fields: (String, TraceValue)*): IO[Unit] =
          local.get.flatMap(_.put(fields: _*))

        def kernel: IO[Kernel] =
          local.get.flatMap(_.kernel)

        def span[A](name: String)(k: IO[A]): IO[A] =
          spanR(name).use(_ => k)

        def spanR(name: String): Resource[IO, Unit] =
          Resource.eval(local.get).flatMap { parent =>
            parent.span(name).flatMap { child =>
              Resource.make(local.set(child))(_ => local.set(parent))
            }
          }

        def traceId: IO[Option[String]] =
          local.get.flatMap(_.traceId)

        def traceUri: IO[Option[URI]] =
          local.get.flatMap(_.traceUri)

      }
    }

  object Implicits {

    /**
     * A no-op `Trace` implementation is freely available for any applicative effect. This lets
     * us add a `Trace` constraint to most existing code without demanding anything new from the
     * concrete effect type.
     */
    implicit def noop[F[_]: Applicative]: Trace[F] =
      new Trace[F] {
        final val void = ().pure[F]
        val kernel: F[Kernel] = Kernel(Map.empty).pure[F]
        def put(fields: (String, TraceValue)*): F[Unit] = void
        def span[A](name: String)(k: F[A]): F[A] = k
        def spanR(name: String): Resource[F, Unit] = Resource.unit
        def traceId: F[Option[String]] = none.pure[F]
        def traceUri: F[Option[URI]] = none.pure[F]
      }

  }

  implicit def kleisliInstance[F[_]: MonadCancelThrow]: KleisliTrace[F] =
    new KleisliTrace[F]

  /**
   * A trace instance for `Kleisli[F, Span[F], *]`, which is the mechanism we use to introduce
   * context into our computations. We can also "lensMap" out to `Kleisli[F, E, *]` given a lens
   * from `E` to `Span[F]`.
   */
  class KleisliTrace[F[_]: MonadCancelThrow] extends Trace[Kleisli[F, Span[F], *]] {

    def kernel: Kleisli[F, Span[F], Kernel] =
      Kleisli(_.kernel)

    def put(fields: (String, TraceValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.put(fields: _*))

    def span[A](name: String)(k: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] =
      Kleisli(_.span(name).use(k.run))

    def spanR(name: String): Resource[Kleisli[F, Span[F], *], Unit] = {
      Resource.apply(Kleisli.ask[F, Span[F]].flatMap { span =>
        parentSpan.span(name).allocated.flatMap { case (span, fin) =>
          Kleisli.local(_ => span)
        }
      })
    }

    // def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, *]] =
    //   new Trace[Kleisli[F, E, *]] {

    //     def kernel: Kleisli[F,E,Kernel] =
    //       Kleisli(e => f(e).kernel)

    //     def put(fields: (String, TraceValue)*): Kleisli[F,E,Unit] =
    //       Kleisli(e => f(e).put(fields: _*))

    //     def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
    //       Kleisli(e => f(e).span(name).use(s => k.run(g(e, s))))

    //     def traceId: Kleisli[F,E,Option[String]] =
    //       Kleisli(e => f(e).traceId)

    //     def traceUri: Kleisli[F,E,Option[URI]] =
    //       Kleisli(e => f(e).traceUri)

    //   }

    def traceId: Kleisli[F, Span[F], Option[String]] =
      Kleisli(_.traceId)

    def traceUri: Kleisli[F, Span[F], Option[URI]] =
      Kleisli(_.traceUri)

  }

  // implicit def liftKleisli[F[_], E](implicit trace: Trace[F]): Trace[Kleisli[F, E, *]] =
  //   new Trace[Kleisli[F, E, *]] {

  //     def put(fields: (String, TraceValue)*): Kleisli[F, E, Unit] =
  //       Kleisli.liftF(trace.put(fields: _*))

  //     def kernel: Kleisli[F, E, Kernel] =
  //       Kleisli.liftF(trace.kernel)

  //     def span[A](name: String)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
  //       Kleisli(e => trace.span[A](name)(k.run(e)))

  //     def traceId: Kleisli[F, E, Option[String]] =
  //       Kleisli.liftF(trace.traceId)

  //     def traceUri: Kleisli[F, E, Option[URI]] =
  //       Kleisli.liftF(trace.traceUri)
  //   }

  // implicit def liftStateT[F[_]: Monad, S](implicit trace: Trace[F]): Trace[StateT[F, S, *]] =
  //   new Trace[StateT[F, S, *]] {

  //     def put(fields: (String, TraceValue)*): StateT[F, S, Unit] =
  //       StateT.liftF(trace.put(fields: _*))

  //     def kernel: StateT[F, S, Kernel] =
  //       StateT.liftF(trace.kernel)

  //     def span[A](name: String)(k: StateT[F, S, A]): StateT[F, S, A] =
  //       StateT(s => trace.span[(S, A)](name)(k.run(s)))

  //     def traceId: StateT[F, S, Option[String]] =
  //       StateT.liftF(trace.traceId)

  //     def traceUri: StateT[F, S, Option[URI]] =
  //       StateT.liftF(trace.traceUri)
  //   }

  // implicit def liftEitherT[F[_]: Functor, E](implicit trace: Trace[F]): Trace[EitherT[F, E, *]] =
  //   new Trace[EitherT[F, E, *]] {

  //     def put(fields: (String, TraceValue)*): EitherT[F, E, Unit] =
  //       EitherT.liftF(trace.put(fields: _*))

  //     def kernel: EitherT[F, E, Kernel] =
  //       EitherT.liftF(trace.kernel)

  //     def span[A](name: String)(k: EitherT[F, E, A]): EitherT[F, E, A] =
  //       EitherT(trace.span(name)(k.value))

  //     def traceId: EitherT[F, E, Option[String]] =
  //       EitherT.liftF(trace.traceId)

  //     def traceUri: EitherT[F, E, Option[URI]] =
  //       EitherT.liftF(trace.traceUri)
  //   }

  // implicit def liftOptionT[F[_]: Functor](implicit trace: Trace[F]): Trace[OptionT[F, *]] =
  //   new Trace[OptionT[F, *]] {

  //     def put(fields: (String, TraceValue)*): OptionT[F, Unit] =
  //       OptionT.liftF(trace.put(fields: _*))

  //     def kernel: OptionT[F, Kernel] =
  //       OptionT.liftF(trace.kernel)

  //     def span[A](name: String)(k: OptionT[F, A]): OptionT[F, A] =
  //       OptionT(trace.span(name)(k.value))

  //     def traceId: OptionT[F, Option[String]] =
  //       OptionT.liftF(trace.traceId)

  //     def traceUri: OptionT[F, Option[URI]] =
  //       OptionT.liftF(trace.traceUri)
  //   }

  // implicit def liftNested[F[_]: Functor, G[_]: Applicative](implicit trace: Trace[F]): Trace[Nested[F, G, *]] =
  //   new Trace[Nested[F, G, *]] {

  //     def put(fields: (String, TraceValue)*): Nested[F, G, Unit] =
  //       trace.put(fields: _*).map(_.pure[G]).nested

  //     def kernel: Nested[F, G, Kernel] =
  //       trace.kernel.map(_.pure[G]).nested

  //     def span[A](name: String)(k: Nested[F, G, A]): Nested[F, G, A] =
  //       trace.span(name)(k.value).nested

  //     def traceId: Nested[F, G, Option[String]] =
  //       trace.traceId.map(_.pure[G]).nested

  //     def traceUri: Nested[F, G, Option[URI]] =
  //       trace.traceUri.map(_.pure[G]).nested
  //   }

}
