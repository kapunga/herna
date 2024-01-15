package herna

import cats.effect.std.Console
import cats.effect.{ExitCode, IO}
import cats.implicits.*
import java.io.File
import scala.concurrent.duration.FiniteDuration
import scopt.{OEffect, OParser}


case class CmdLineOpts(ip: Option[String], ttl: Option[FiniteDuration], configFile: Option[File])

object CmdLineOpts {
  case class CmdParseError(exitCode: ExitCode) extends Exception("Configuration Parsing Failed")
  
  private val Default: CmdLineOpts = CmdLineOpts(None, None, None)

  def parse(args: List[String]): IO[CmdLineOpts] = {
    val parser = buildParser

    val (parsedOptions, eff) = OParser.runParser[CmdLineOpts](parser, args, Default)

    parsedOptions match {
      case Some(cmdLineOpts) => IO.pure(cmdLineOpts)
      case None => printEffects(eff) *> IO.raiseError(CmdParseError(exitCode(eff)))
    }
  }

  private def buildParser: OParser[Unit, CmdLineOpts] = {
    val builder = OParser.builder[CmdLineOpts]
    import builder.*

    OParser.sequence(
      programName("herna"),
      head("herna", "0.1"),
      opt[String]('i', "ip")
        .action((x, c) => c.copy(ip = Some(x)))
        .optional()
        .text("ip is a string property"),
      opt[FiniteDuration]('t', "ttl")
        .action((x, c) => c.copy(ttl = Some(x)))
        .optional()
        .text("ttl is a Duration property"),
      opt[File]('c', "config")
        .action((x, c) => c.copy(configFile = Some(x)))
        .optional()
        .text("config is a string property")
    )
  }

  private def printEffects(effects: List[OEffect]): IO[Unit] = {
    effects.traverse({
      case OEffect.DisplayToOut(msg) => Console[IO].println(msg)
      case OEffect.DisplayToErr(msg) => Console[IO].errorln(msg)
      case OEffect.ReportError(msg) => Console[IO].errorln(msg)
      case OEffect.ReportWarning(msg) => Console[IO].errorln(msg)
      case OEffect.Terminate(_) => IO.unit
    }) *> IO.unit
  }

  private def exitCode(effects: List[OEffect]): ExitCode =
    effects.find({
      case OEffect.Terminate(Left(_)) => true
      case _ => false
    }).fold(ExitCode.Success)(_ => ExitCode.Error)
}
