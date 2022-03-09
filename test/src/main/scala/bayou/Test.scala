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

import cats.effect.{Trace => _, _}
import cats.implicits._
import cats.effect.syntax.all._
import fs2._
import bayou.Trace
import bayou.Trace._
import natchez.log.Log
import natchez.{Trace => _, _}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._
import java.net.URI
import cats.effect.std.Semaphore
import fs2.concurrent.Channel
import cats.effect.std.Queue


object Merge extends IOApp.Simple with Setup {
    def run: IO[Unit] = {
      trace.use { implicit trace =>
        stream.map(_ => ()).compile.drain
      }
    }

}