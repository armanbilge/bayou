package bayou

import cats.effect.{Trace => _, _}
import cats.implicits._
import fs2._
import bayou.Trace
import bayou.Trace._
import natchez.log.Log
import natchez.{Trace => _, _}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._
import java.net.URI

trait Setup {
  
    val entryPoint = Log.entryPoint[IO]("example")

    val trace = entryPoint.root("root").flatMap(r => Resource.eval(Trace.ioTrace(r)))

    def stream(implicit trace: IOTrace): Stream[IO, Unit] = {
        Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
          .covary[IO]
          .chunkN(3)
          .flatMap { chunk =>
            for {
            _    <- Stream.resource(trace.spanR("green kangaroo"))
            _    <- Stream.resource(trace.spanR("child"))
            _    <- Stream.eval(trace.put("vals" -> chunk.toList.mkString(",")))
            _    <- Stream.sleep[IO](1.second)
            } yield ()
          }
    }

  implicit def logger: Logger[IO] =
    new Logger[IO] {
      def error(message: => String): IO[Unit] =
        IO.println(s"[error] $message\n")
      def warn(message: => String): IO[Unit] =
        IO.println(s"[warn] $message\n")
      def info(message: => String): IO[Unit] =
        IO.println(s"[info] $message\n")
      def debug(message: => String): IO[Unit] =
        IO.println(s"[debug] $message\n")
      def trace(message: => String): IO[Unit] =
        IO.println(s"[trace] $message\n")
      def error(t: Throwable)(message: => String): IO[Unit] =
        IO.println(s"[error] $message\n${t.getMessage}")
      def warn(t: Throwable)(message: => String): IO[Unit] =
        IO.println(s"[warn] $message\n${t.getMessage}")
      def info(t: Throwable)(message: => String): IO[Unit] =
        IO.println(s"[info] $message\n${t.getMessage}")
      def debug(t: Throwable)(message: => String): IO[Unit] =
        IO.println(s"[debug] $message\n${t.getMessage}")
      def trace(t: Throwable)(message: => String): IO[Unit] =
        IO.println(s"[trace] $message\n${t.getMessage}")
  }
}
