/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 19/02/12
 * Time: 16:25
 * Global settings class
 */

import play.api._
import play.api.mvc._
import play.api.mvc.Results._

object Global extends GlobalSettings {

  /**
   * Executed before start of application
   */
  override def beforeStart(app : play.api.Application) = {
    super.beforeStart(app)
  }

  /**
   * Executed on start of application
   * We load DB data in here
   */
  override def onStart(app: Application) {
    super.onStart(app)
  }

  /**
   * Error in the application
   * We use the same scenario as Not Found to hide options from hackers but we notify ourselves with email
   */
  override def onError(request: RequestHeader, ex: Throwable) = {
    InternalServerError(
      views.html.errors.error(ex)
    )
    //TODO: send mail?
  }

  /**
   * Route not found
   */
  override def onHandlerNotFound(request: RequestHeader) = {
    NotFound(
      views.html.errors.error404(request.path)
    )
  }

  /**
   * Route was found but we couldn't bind the parameters
   * We use the same scenario as Not Found to hide options from hackers but we notify ourselves with email
   */
  override def onBadRequest(request : play.api.mvc.RequestHeader, error : scala.Predef.String) = {
    NotFound(
      views.html.errors.error404(request.path)
    )
    //TODO: send mail?
  }

}