package controllers

import play.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}
import play.api.i18n.Messages
import com.typesafe.plugin._
import play.api.Play.current

/**
 * Created with IntelliJ IDEA.
 * User: pvillega
 * Date: 12/05/12
 * Time: 16:32
 * Controller to manage user feedback
 */

/* Class to store Feedback in Play form temporally on the request */
case class Feedback(content: String, email: String, name: String)

object Feedback extends Controller {

  // form to store feedback
  val fbForm: Form[Feedback] = Form(
    mapping(
      "content" -> nonEmptyText,
      "name" -> nonEmptyText,
      "email" -> email
    ) (Feedback.apply)(Feedback.unapply)
  )

  /**
   * Displays the feedback page
   */
  def index = Action {
    implicit request =>
      Logger.info("Feedback.index accessed ")
      Ok(views.html.feedback.index(fbForm))

  }

  /**
   * Gets the feedback and sends it via email to the admins
   */
  def send = Action {
    implicit request =>
      Logger.info("Feedback.send accessed")
      fbForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.error("Feedback.send errors while obtaining Feedback: %s".format(errors))
          BadRequest(views.html.feedback.index(errors))
        },
        // We got a valid value, update
        fb => {
          Logger.info("Feedback.send sending Feedback to admins[%s]".format(fb))

          val mail = use[MailerPlugin].email
          mail.setSubject("Feedback from Play Modules")
          mail.addRecipient("Administrator <%s>".format(Application.errorReportingMail), Application.errorReportingMail)
          mail.addFrom("Play Modules <noreply@playmodules.net>")
          mail.send("Feedback sent by: %s \n\n Email: %s \n\n Comment: \n %s  \n\n".format(fb.name, fb.email, fb.content) )

          Redirect(routes.Application.index()).flashing("success" -> Messages("feedback.sent"))
        }
      )

  }
}
