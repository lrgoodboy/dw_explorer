package com.anjuke.dw.explorer

import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.util.resource.Resource

class WebjarsServlet extends DefaultServlet {

  override def getResource(pathInContext: String): Resource = {
    val url = getClass.getResource("/META-INF/resources" + pathInContext)
    Resource.newResource(url)
  }

}
