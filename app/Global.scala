/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 19/02/12
 * Time: 16:25
 * Global settings class
 */

import controllers.Application
import play.api._
import mvc._
import play.api.mvc.Results._
import play.api.Play.current
import com.typesafe.plugin._

object Global extends GlobalSettings {

  /**
   * Executed before start of application
   */
  override def beforeStart(app : play.api.Application) = {
    Logger.info("beforeStart executed for application %s".format(app.mode))
    super.beforeStart(app)
  }

  /**
   * Executed on start of application
   * We load DB data in here
   */
  override def onStart(app: Application) {
    Logger.info("onStart executed for application %s".format(app.mode))
    super.onStart(app)
  }

  /**
   * Error in the application
   * We use the same scenario as Not Found to hide options from hackers but we notify ourselves with email
   */
  override def onError(request: RequestHeader, ex: Throwable) = {
    Logger.error("onError executed for request %s".format(request), ex)

    val mail = use[MailerPlugin].email
    mail.setSubject("Error in Play Modules")
    mail.addRecipient("Administrator <%s>".format(Application.errorReportingMail), Application.errorReportingMail)
    mail.addFrom("Play Modules <noreply@playmodules.net>")
    mail.send( "Error detected in Play modules. \n Request: \n %s  \n\n Exception: \n %s \n\n %s".format(request, ex.getMessage, ex.getStackTrace.toList.mkString("\n")) )

    InternalServerError(
      views.html.errors.error(ex)(request)
    )
  }

  /**
   * Route not found
   */
  override def onHandlerNotFound(request: RequestHeader) = {
    Logger.error("onHandlerNotFound executed for request %s".format(request))

    NotFound(
      views.html.errors.error404(request.path)(request)
    )
  }

  /**
   * Route was found but we couldn't bind the parameters
   * We use the same scenario as Not Found to hide options from hackers but we notify ourselves with email
   */
  override def onBadRequest(request : RequestHeader, error : scala.Predef.String) = {
    Logger.error("onBadRequest executed for request %s on error %s".format(request, error))

    val mail = use[MailerPlugin].email
    mail.setSubject("Bad Request in Play Modules")
    mail.addRecipient("Administrator <%s>".format(Application.errorReportingMail), Application.errorReportingMail)
    mail.addFrom("Play Modules <noreply@playmodules.net>")
    mail.send( "Bad Request received in Play modules\n. Request: %s  \n\n Error: \n %s".format(request, error) )

    NotFound(
      views.html.errors.error404(request.path)(request)
    )
  }

}