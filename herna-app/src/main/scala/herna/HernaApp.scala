package herna

import cats.effect.*
import cats.effect.std.Console
import org.ekrich.config.ConfigException
import scala.concurrent.duration.*

object HernaApp extends IOApp {
  import TraefikSettings.given

  override def run(args: List[String]): IO[ExitCode] =
    runApp(args).handleError({
      case CmdLineOpts.CmdParseError(ec) => ec
      case _                             => ExitCode.Error
    })

  private def runApp(args: List[String]): IO[ExitCode] = {
    for {
      ts <- loadSettings(args)
      _ <- Console[IO].println(ts)
      _ <- fs2.Stream.repeatEval(Console[IO].println(s"Still running!"))
        .metered(1.second).compile.drain
        .onCancel(Console[IO].println(s"\nInterrupted, shutting down!"))
    } yield ExitCode.Success
  }

  private def loadSettings(args: List[String]): IO[TraefikSettings] =
    for {
      cmd  <- CmdLineOpts.parse(args)
      _    <- Console[IO].println("Loading configuration...")
      conf <- HernaConfig.loadConfig(cmd).onError {
        case t: Throwable => Console[IO].errorln(t.getMessage)
      }
    } yield TraefikSettings.build(cmd, conf)
}
