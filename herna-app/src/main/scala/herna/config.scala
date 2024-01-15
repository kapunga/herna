package herna

import cats.effect.IO
import java.io.File
import org.ekrich.config.{Config, ConfigFactory, ConfigValueFactory}
import scala.jdk.CollectionConverters.given

@scala.annotation.implicitNotFound("Missing given Extractor for type ${T}, make sure you define one!")
trait Extractor[T] {
  def extract(config: Config, path: String): T
}

object Extractor {
  given Extractor[String] with
    def extract(config: Config, path: String): String = config.getString(path)

  given Extractor[Int] = (config, path) => config.getInt(path)
  given Extractor[Long] = (config, path) => config.getLong(path)
  given Extractor[Float] = (config, path) => config.getNumber(path).floatValue()
  given Extractor[Double] = (config, path) => config.getDouble(path)
  given Extractor[Boolean] = (config, path) => config.getBoolean(path)

  given optionExtractor[T](using ex: Extractor[T]): Extractor[Option[T]] =
    (config, path) => if (config.hasPath(path)) Some(ex.extract(config, path)) else None

  given stringListExtractor: Extractor[List[String]] =
    (config, path) => config.getStringList(path).asScala.toList
  given intListExtractor: Extractor[List[Int]] =
    (config, path) => config.getIntList(path).asScala.toList.map(_.toInt)
  given longListExtractor: Extractor[List[Long]] =
    (config, path) => config.getLongList(path).asScala.toList.map(_.toLong)
  given floatListExtractor: Extractor[List[Float]] =
    (config, path) => config.getNumberList(path).asScala.toList.map(_.floatValue())
  given doubleListExtractor: Extractor[List[Double]] =
    (config, path) => config.getDoubleList(path).asScala.toList.map(_.toDouble)
  given booleanListExtractor: Extractor[List[Boolean]] =
    (config, path) => config.getBooleanList(path).asScala.toList.map(_.booleanValue)

  given listExtractor[T](using ex: Extractor[T]): Extractor[List[T]] =
    (config, path) => {
      def wrap(conf: Config): Config =
        ConfigValueFactory.fromMap(Map("key" -> conf.root.unwrapped).asJava).toConfig
      config.getConfigList(path).asScala.toList.map(c => ex.extract(wrap(c), "key"))
    }

  given setExtractor[T](using ex: Extractor[List[T]]): Extractor[Set[T]] =
    (config, path) => ex.extract(config, path).toSet

  given mapExtractor[T](using ex: Extractor[T]): Extractor[Map[String, T]] =
    (config, path) => {
      val c = config.getConfig(path)
      val keys = c.entrySet.asScala.map(_.getKey.split('.').head)
      keys.map(k => k -> ex.extract(c, k)).toMap
    }
}

extension (config: Config)
  def get[T](path: String)(using ex: Extractor[T]): T =
    ex.extract(config, path)

  def getOrElse[T](path: String, default: T)(using ex: Extractor[Option[T]]): T =
    ex.extract(config, path).getOrElse(default)

case class HernaConfig(
  hernaSettings: HernaConfig.HernaSettings,
  redisConfig: HernaConfig.RedisConfig,
  services: List[HernaConfig.Service])

object HernaConfig {
  case class HernaSettings(rootDomain: String, certResolver: String)
  object HernaSettings {
    given Extractor[HernaSettings] = (config, path) => {
      val c = config.getConfig(path)
      val rootDomain = c.get[String]("root_domain")
      val certResolver = c.get[String]("cert_resolver")

      HernaSettings(rootDomain, certResolver)
    }
  }
  case class RedisConfig(host: String, port: Int)
  object RedisConfig {
    given Extractor[RedisConfig] = (config, path) => {
      val c = config.getConfig(path)
      val host = c.get[String]("host")
      val port = c.get[Int]("port")

      RedisConfig(host, port)
    }
  }

  case class Service(name: String, entryPoint: String, subdomain: String, port: Int)
  object Service {
    given Extractor[Service] = (config, path) => {
      val c = config.getConfig(path)
      val name = path.split('.').last
      val entryPoint = c.get[String]("entry_point")
      val subdomain = c.get[String]("subdomain")
      val port = c.get[Int]("port")

      Service(name, entryPoint, subdomain, port)
    }
  }

  private val DefaultConfigFile: File = new File(s"${System.getProperty("user.home")}/.herna.conf")

  given Extractor[HernaConfig] = (config, path) => {
    val c = config.getConfig(path)

    val hernaSettings = c.get[HernaSettings]("settings")
    val redisConfig = c.get[RedisConfig]("redis")
    val services = c.get[Map[String, Service]]("service").values.toList

    HernaConfig(hernaSettings, redisConfig, services)
  }

  def loadConfig(cmdLineOpts: CmdLineOpts): IO[HernaConfig] = {
    val configFile = cmdLineOpts.configFile.getOrElse(DefaultConfigFile)

    for {
      config <- IO(configFromString(configFile))
      hernaConf <- IO(config.get[HernaConfig]("herna"))
    } yield hernaConf
  }

  private def configFromString(file: File): Config = {
    val source = scala.io.Source.fromFile(file)
    val configString = source.getLines().mkString("\n")

    ConfigFactory.parseString(configString)
  }
}



