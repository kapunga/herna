package herna

import io.chrisdavenport.rediculous.*
import cats.effect.{IO, Resource}
import cats.effect.std.Console
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.io.net.*
import fs2.*
import java.net.InetAddress
import scala.concurrent.duration.*

object AppLogic {
  def buildProgram(cmd: CmdLineOpts, hc: HernaConfig): IO[Unit] =
    makeConnection(hc).use(redis => fs2.Stream.emits(hc.services)
      .parEvalMapUnbounded(buildServiceRefreshEffect(redis, cmd, hc.hernaSettings, _))
      .compile.drain)

  private def buildServiceRefreshEffect(
    redis: RedisConnection[IO],
    cmd: CmdLineOpts,
    settings: HernaConfig.HernaSettings,
    service: HernaConfig.Service): IO[Unit] = {

    val ip = cmd.ip.getOrElse(InetAddress.getLocalHost.getHostAddress)
    val ttl = cmd.ttl.getOrElse(15.seconds)
    val refresh = (ttl.toMillis * 0.95).milliseconds
    val traefikSettings = TraefikSettings(settings, ip, service)

    Console[IO].println(s"Setting up refresh for service: '${service.name}' every $refresh") *>
      fs2.Stream.repeatEval(applySettings(redis, refresh, service.name, traefikSettings))
        .meteredStartImmediately(refresh).compile.drain
        .onCancel(removeSettings(redis, service.name, traefikSettings))
  }

  private def applySettings(redis: RedisConnection[IO], timeout: Duration, serviceName: String, traefikSettings: TraefikSettings): IO[Unit] = {
    val setOps = RedisCommands.SetOpts(None, Some(timeout.toMillis), None, false)

    val logEffect = Console[IO].println(s"Refreshing settings for service '$serviceName' with duration of $timeout.")

    val setEffect = traefikSettings.map({
      case (k, v) => RedisCommands.set[Redis[IO, *]](k, v, setOps)
    }).parSequence.run(redis)

    for {
      _ <- logEffect
      res <- setEffect
      _ <- Console[IO].println(res)
    } yield ()
  }

  private def removeSettings(redis: RedisConnection[IO], serviceName: String, traefikSettings: TraefikSettings): IO[Unit] = {
    Console[IO].println(s"\nInterrupted, clearing settings for service '$serviceName'.") <* traefikSettings.map({
      case (k, _) => RedisCommands.del[Redis[IO, *]](k)
    }).parSequence.run(redis)
  }

  private def makeConnection(hc: HernaConfig): Resource[IO, RedisConnection[IO]] =
    RedisConnection
      .queued[IO]
      .withHost(Host.fromString(hc.redisConfig.host).get)
      .withPort(Port.fromInt(hc.redisConfig.port).getOrElse(port"6379"))
      .withMaxQueued(10000)
      .withWorkers(2)
      .build
}
