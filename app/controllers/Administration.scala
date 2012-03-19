package controllers

import play.api.mvc.Controller
import models.User
import play.api.data.Form
import play.api.data.Forms._
import play.Logger
import play.api.i18n.Messages

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 19/03/12
 * Time: 12:08
 * Controller for administration area
 */

object Administration extends Controller with Secured {

  // form to edit users as admin
  val userForm: Form[User] = Form(
    mapping(
      "name" -> nonEmptyText,
      "githubId" -> optional(longNumber verifying( _ > 0)),
      "twitterId" -> optional(longNumber verifying( _ > 0)),
      "googleId" -> optional(text),
      "disabled" -> boolean,
      "admin" -> boolean,
      "avatar" -> optional(text),
      "url" -> optional(text),
      "location" -> optional(text),
      "bio" -> optional(text)
    ) {
      // The mapping signature doesn't match the User case class signature,
      // so we have to define custom binding/unbinding functions {
      // Binding: Create a User from the mapping result (ignore the second password and the accept field)
      (name, githubId, twitterId, googleId, disabled, admin, avatar, url, location, bio) => User(name = name, githubId = githubId, twitterId = twitterId, googleId = googleId, disabled = disabled, admin = admin, avatar = avatar, url = url, location = location, bio = bio)
    } {
      // Unbinding: Create the mapping values from an existing User value
      user: User => Some(user.name, user.githubId, user.twitterId, user.googleId, user.disabled, user.admin, user.avatar, user.url, user.location, user.bio)
    }
  )

  /**
   * Renders the main administration menu
   */
  def index() = IsAdmin {
    implicit request =>
      Logger.info("Administration.edit accessed by user %d".format(request.user.id.get))
      Ok(views.html.administration.index())
  }

  /**
   * User administration screen
   */
  def users() = IsAdmin {
    implicit request =>
      Logger.info("Administration.users accessed by user %d".format(request.user.id.get))
      Redirect(routes.Administration.listUsers())
  }

  /**
   * Display the paginated list of users.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param filter Filter applied on user names
   */
  def listUsers(page: Int, orderBy: Int, filter: String) = IsAdmin {
    implicit request =>
      Logger.info("Administration.listUsers accessed by user %d with params page[%d] orderBy[%d] filter[%s]".format(request.user.id.get, page, orderBy, filter))
      Ok(views.html.administration.users(
        User.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%")),
        orderBy,
        filter
      ))
  }

  /**
   * Edits the selected user
   *
   * @param id the id of the user to edit
   */
  def editUser(id: Long) = IsAdmin {
    implicit request =>
      Logger.info("Administration.editUser accessed by user %d to edit user[%d]".format(request.user.id.get, id))
      User.findById(id) match {
        case Some(user) => Ok(views.html.administration.editUser(userForm.fill(user), user.id.get))
        case _ => NotFound(views.html.errors.error404(request.path)(request))
      }
  }

  /**
   * Saves the changes to the user
   *
   * @param id the id of the user we modified
   */
  def saveUser(id: Long) = IsAdmin {
    implicit request =>
      userForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => BadRequest(views.html.administration.editUser(errors, id)),
        // We got a valid User value, update
        user => {
          User.updateUserAdministration(id, user)
          Redirect(routes.Administration.editUser(id)).flashing("success" -> Messages("useradmin.updated"))
        }
      )
  }

  def versions() = TODO

}
