package herna

import cats.Show
import java.net.InetAddress

type TraefikSettings = List[(String, String)]

object TraefikSettings {
  given Show[TraefikSettings] with
    override def show(ts: TraefikSettings): String =
      s"Traefik Settings:\n${ts.map(t => s"'${t._1}' -> '${t._2}'").mkString("\n")}"

  def apply(hernaSettings: HernaConfig.HernaSettings, ip: String, service: HernaConfig.Service): TraefikSettings = {
    val routerName = service.name
    val fullHost = s"${service.subdomain}.${hernaSettings.rootDomain}"
    val url = s"http://$ip:${service.port}"

    List(
      s"traefik/http/routers/$routerName/rule" -> s"Host(`$fullHost`)",
      s"traefik/http/routers/$routerName/entrypoints/0" -> service.entryPoint,
      s"traefik/http/routers/$routerName/service" -> service.name,
      s"traefik/http/routers/$routerName/tls/certresolver" -> hernaSettings.certResolver,
      s"traefik/http/routers/$routerName/tls/domains/0/main" -> hernaSettings.rootDomain,
      s"traefik/http/routers/$routerName/tls/domains/0/sans/0" -> fullHost,
      s"traefik/http/services/$routerName/loadbalancer/servers/0/url" -> url)
  }
}
