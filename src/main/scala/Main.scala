import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{DefaultServlet, ServletContextHandler}
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener
import com.anjuke.dw.explorer.util.Config

object Main extends App {

  val context = new WebAppContext()
  context.setContextPath(Config("common", "server.context.path"))
  context.setResourceBase("src/main/webapp")
  context.addEventListener(new ScalatraListener)
  context.addServlet(classOf[DefaultServlet], "/")

  val server = new Server(Config("common", "server.port").toInt)
  server.setHandler(context)
  server.start
  server.join
}
