package herna

import cats.effect.IO
import cats.effect.std.Console
import scala.concurrent.duration.Duration

object AppLogic {
  def applySettings(timeout: Duration, serviceName: String, traefikSettings: TraefikSettings): IO[Unit] = {
    Console[IO].println(s"Applying settings for service '$serviceName' with duration of $timeout.")
  }

  def removeSettings(serviceName: String, traefikSettings: TraefikSettings): IO[Unit] = {
    Console[IO].println(s"Removing settings for service '$serviceName'.")
  }
}
