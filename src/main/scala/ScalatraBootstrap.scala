import org.scalatra.LifeCycle
import com.anjuke.dw.explorer.MyScalatraServlet
import com.anjuke.dw.explorer.QueryEditorServlet
import com.anjuke.dw.explorer.QueryTaskServlet
import javax.servlet.ServletContext
import com.anjuke.dw.explorer.init.DatabaseInit
import akka.actor.ActorSystem
import akka.actor.Props
import com.anjuke.dw.explorer.TaskActor
import akka.routing.SmallestMailboxRouter
import com.anjuke.dw.explorer.util.Config
import com.typesafe.config.ConfigFactory
import com.anjuke.dw.explorer.WebjarsServlet

class ScalatraBootstrap extends LifeCycle with DatabaseInit {

  val actorSystem = {

    val config = ConfigFactory.parseString(Seq(
      "akka.remote.netty.hostname = \"%s\"" format Config("service", "akka.remote.netty.hostname"),
      "akka.remote.netty.port = %s" format Config("service", "akka.remote.netty.port")
    ) mkString "\n").withFallback(ConfigFactory.load)

    ActorSystem("queryEditor", config)
  }

  val taskActor = actorSystem.actorOf(Props(new TaskActor(actorSystem)).withRouter(SmallestMailboxRouter(10)), "taskActor")

  override def init(context: ServletContext) {
    configureDb()
    context.mount(new MyScalatraServlet, "/*")
    context.mount(new QueryEditorServlet(taskActor), "/query-editor/*")

    // It may be not the best way to inject the dependency.
    context.setAttribute("actorSystem", actorSystem)
    context.mount(classOf[QueryTaskServlet], "/query-task/*")

    // webjars
    context.mount(classOf[WebjarsServlet], "/webjars/*")
  }

  override def destroy(context: ServletContext) {
    closeDbConnection()
    actorSystem.shutdown()
  }

}
