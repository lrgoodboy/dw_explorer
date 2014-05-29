import org.scalatra.LifeCycle
import com.anjuke.dw.explorer.MyScalatraServlet
import com.anjuke.dw.explorer.QueryEditorServlet
import javax.servlet.ServletContext
import com.anjuke.dw.explorer.init.DatabaseInit

class ScalatraBootstrap extends LifeCycle with DatabaseInit {

  override def init(context: ServletContext) {
    configureDb()
    context.mount(new MyScalatraServlet, "/*")
    context.mount(new QueryEditorServlet, "/query-editor/*")
  }

  override def destroy(context: ServletContext) {
    closeDbConnection()
  }

}
