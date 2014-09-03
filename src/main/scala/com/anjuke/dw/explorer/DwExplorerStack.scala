package com.anjuke.dw.explorer

import scala.collection.mutable
import org.fusesource.scalate.TemplateEngine
import org.fusesource.scalate.layout.DefaultLayoutStrategy
import org.scalatra.ScalatraServlet
import org.scalatra.scalate.ScalateSupport
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

trait DwExplorerStack extends ScalatraServlet with ScalateSupport {

  private val logger = LoggerFactory.getLogger(getClass)

  /* wire up the precompiled templates */
  override protected def defaultTemplatePath: List[String] = List("/WEB-INF/templates/views")
  override protected def createTemplateEngine(config: ConfigT) = {
    val engine = super.createTemplateEngine(config)
    engine.layoutStrategy = new DefaultLayoutStrategy(engine,
      TemplateEngine.templateTypes.map("/WEB-INF/templates/layouts/default." + _): _*)
    engine.packagePrefix = "templates"
    engine
  }
  /* end wiring up the precompiled templates */

  override protected def templateAttributes(implicit request: HttpServletRequest): mutable.Map[String, Any] = {
    super.templateAttributes ++ mutable.Map.empty // Add extra attributes here, they need bindings in the build file
  }

  notFound {
    // remove content type in case it was set through an action
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

}
