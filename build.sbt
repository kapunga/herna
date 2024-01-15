ThisBuild / organization := "org.kapunga"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.1"

ThisBuild / scalacOptions ++= List(
  "-Ykind-projector",
  "-Xfatal-warnings",
  "-J-Xmx2G")

import scala.scalanative.build.*

lazy val hernaApp = (project in file("herna-app"))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "Herna App",
    logLevel := Level.Info,
    libraryDependencies ++= Seq(
      "co.fs2"            %%% "fs2-core"   % "3.10-365636d",
      "co.fs2"            %%% "fs2-io"     % "3.10-365636d",
      "io.chrisdavenport" %%% "rediculous" % "0.5.1",
      "org.ekrich"        %%% "sconfig"    % "1.6.0",
      "com.github.scopt"  %%% "scopt"      % "4.1.0",
    ),
    resolvers += "s01-oss-sonatype-org-snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
    nativeLinkStubs := true,
    nativeConfig ~= { c =>
    c.withLTO(LTO.none) // thin
      .withBuildTarget(BuildTarget.application)
      .withMode(Mode.debug) // releaseFast
      .withGC(GC.immix) // commix
    }
  )
