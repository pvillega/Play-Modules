package controllers

import play.api.mvc._
import models.User
import play.Logger
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.Messages

/**
 * Controller for the profile-related actions
 */
object Profile extends Controller with Secured {


  val profileForm: Form[User] = Form(
    mapping(
      "name" -> nonEmptyText,
      "avatar" -> optional(text),
      "bio" -> optional(text),
      "url" -> optional(text),
      "location" -> optional(text)
    ) {
      // The mapping signature doesn't match the User case class signature,
      // so we have to define custom binding/unbinding functions {
      // Binding: Create a User from the mapping result (ignore the second password and the accept field)
      (name, avatar, bio, url, location) => User(name = name, avatar = avatar, bio = bio, url = url, location = location)
    } {
      // Unbinding: Create the mapping values from an existing User value
      user: User => Some(user.name, user.avatar, user.bio, user.url, user.location)
    }
  )


  /**
   * Shows the profile of the requested user
   * @param id the id of the user we are checking
   * @return the profile for the requested user or not found
   */
  def index(id: Long) = Action {
    implicit request =>
      val loggedId = session.get("userId").getOrElse("-1").toLong
      Logger.info("Profile.index with id %d accessed by user %s".format(id, loggedId))
      val itsMe = id == loggedId
      User.findById(id) match {
        case None => NotFound(views.html.errors.error404(request.path)(request))
        case Some(user) => Ok(views.html.profile.index(user, itsMe))
      }
  }

  /**
   * Edits the profile of the authenticated user
   *
   */
  def edit() = Authenticated {
    implicit request =>
      Logger.info("Profile.edit accessed by user %d".format(request.user.id.get))
      Ok(views.html.profile.edit(profileForm.fill(request.user), request.user.id.get))
  }

  /**
   * Saves the modified user profile
   */
  def save() = Authenticated {
    implicit request =>
      Logger.info("Profile.save accessed by user %d".format(request.user.id.get))
      profileForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => BadRequest(views.html.profile.edit(errors, request.user.id.get)),
        // We got a valid User value, update
        user => {
          User.updateUser(request.user.id.get, user)
          Redirect(routes.Profile.index(request.user.id.get)).flashing("success" -> Messages("profile.updated"))
        }
      )
  }

}

//TODO: add pjax for all anchor not POST
//TODO: add logout menu when logged in, as POST request (ensure Form avoids fake request)
//TODO: check selected menu at top is the correct one (does jquery onload works fine with pjax request?)
//TODO: create CRUD page for admins to manage users (form + edit/update/remove) < see computer demo for pagination, edit, etc
//TODO: create pages to manage projects