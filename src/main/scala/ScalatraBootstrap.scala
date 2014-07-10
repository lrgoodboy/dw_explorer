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

class ScalatraBootstrap extends LifeCycle with DatabaseInit {

  val actorSystem = ActorSystem("queryEditor")
  val taskActor = actorSystem.actorOf(Props[TaskActor].withRouter(SmallestMailboxRouter(10)), "taskActor")

  override def init(context: ServletContext) {
    configureDb()
    context.mount(new MyScalatraServlet, "/*")
    context.mount(new QueryEditorServlet(taskActor), "/query-editor/*")
    context.mount(new QueryTaskServlet, "/query-task/*")
  }

  override def destroy(context: ServletContext) {
    closeDbConnection()
    actorSystem.shutdown()
  }

}
