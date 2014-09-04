import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._
import com.earldouglas.xsbtwebplugin.PluginKeys._

object DwExplorerBuild extends Build {
  val Organization = "com.anjuke.dw"
  val Name = "DW Explorer"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.3"
  val ScalatraVersion = "2.2.2"
  val JettyVersion = "8.1.8.v20121106"

  lazy val project = Project (
    "dw_explorer",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ scalateSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % JettyVersion % "container",
        "org.eclipse.jetty" % "jetty-plus" % JettyVersion % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s" %% "json4s-jackson" % "3.2.10",
        "org.squeryl" %% "squeryl" % "0.9.5-6",
        "mysql" % "mysql-connector-java" % "5.1.30",
        "c3p0" % "c3p0" % "0.9.1.2",
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.1",
        "org.apache.poi" % "poi-ooxml" % "3.10-FINAL",
        "commons-configuration" % "commons-configuration" % "1.10",
        "org.eclipse.jetty" % "jetty-websocket" % JettyVersion % "container;compile",
        "com.typesafe.akka" %% "akka-remote" % "2.1.2"
      ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile){ base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty,  /* default imports should be added here */
            Seq(
              Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
            ),  /* add extra bindings here */
            Some("templates")
          )
        )
      },
      env in Compile := Some(file(".") / "jetty-env.xml" asFile)
    )
  )
}
