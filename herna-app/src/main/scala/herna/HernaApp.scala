package herna

import cats.effect.*
import cats.effect.std.Console

object HernaApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    runApp(args).handleError({
      case CmdLineOpts.CmdParseError(ec) => ec
      case _                             => ExitCode.Error
    })

  private def runApp(args: List[String]): IO[ExitCode] = {
    for {
      cc <- loadSettings(args)
      _  <- AppLogic.buildProgram.tupled(cc)
    } yield ExitCode.Success
  }

  private def loadSettings(args: List[String]): IO[(CmdLineOpts, HernaConfig)] =
    for {
      cmd  <- CmdLineOpts.parse(args)
      _    <- Console[IO].println("Loading configuration...")
      conf <- HernaConfig.loadConfig(cmd).onError {
        case t: Throwable => Console[IO].errorln(t.getMessage)
      }
    } yield (cmd, conf)
}
