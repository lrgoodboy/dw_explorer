import org.scalatra.LifeCycle
import com.anjuke.dw.explorer.MyScalatraServlet
import com.anjuke.dw.explorer.QueryEditorServlet
import javax.servlet.ServletContext
import com.anjuke.dw.explorer.init.DatabaseInit
import akka.actor.ActorSystem
import akka.actor.Props
import com.anjuke.dw.explorer.TaskActor
import akka.routing.SmallestMailboxRouter

class ScalatraBootstrap extends LifeCycle with DatabaseInit {

  val actorSystem = ActorSystem()
  val taskActor = actorSystem.actorOf(Props[TaskActor].withRouter(SmallestMailboxRouter(10)))

  override def init(context: ServletContext) {
    configureDb()
    context.mount(new MyScalatraServlet, "/*")
    context.mount(new QueryEditorServlet(taskActor), "/query-editor/*")
  }

  override def destroy(context: ServletContext) {
    closeDbConnection()
    actorSystem.shutdown()
  }

}
