package herna

import cats.effect.IO
import cats.effect.std.Console
import cats.implicits.*
import java.net.InetAddress
import scala.concurrent.duration.*

object AppLogic {
  def buildProgram(cmd: CmdLineOpts, hc: HernaConfig): IO[Unit] =
    fs2.Stream.emits(hc.services)
      .parEvalMapUnbounded(buildServiceRefreshEffect(cmd, hc.hernaSettings, _))
      .compile.drain

  def buildServiceRefreshEffect(cmd: CmdLineOpts, settings: HernaConfig.HernaSettings, service: HernaConfig.Service): IO[Unit] = {
    val ip = cmd.ip.getOrElse(InetAddress.getLocalHost.getHostAddress)
    val ttl = cmd.ttl.getOrElse(15.seconds)
    val refresh = (ttl.toMillis * 0.95).milliseconds
    val traefikSettings = TraefikSettings(settings, ip, service)

    Console[IO].println(s"Setting up refresh for service: '${service.name}' every $refresh") *>
      fs2.Stream.repeatEval(applySettings(refresh, service.name, traefikSettings))
        .meteredStartImmediately(refresh).compile.drain
        .onCancel(removeSettings(service.name, traefikSettings))
  }

  def applySettings(timeout: Duration, serviceName: String, traefikSettings: TraefikSettings): IO[Unit] = {
    Console[IO].println(s"Refreshing settings for service '$serviceName' with duration of $timeout.")
  }

  def removeSettings(serviceName: String, traefikSettings: TraefikSettings): IO[Unit] = {
    Console[IO].println(s"\nInterrupted, removing settings for service '$serviceName'.")
  }
}
