package controllers

import play.api.mvc._
import models.User
import play.Logger

/**
 * Controller for the profile-related actions
 */
object Profile extends Controller {

  /**
   * Shows the profile of the requested user
   * @param id the id of the user we are checking
   * @return the profile for the requested user or not found
   */
  def index(id: Long) = Action { implicit request =>
    val loggedId = session.get("userId").getOrElse("-1").toLong
    Logger.info("Profile.index with id %d accessed by user %s".format(id, loggedId))
    val itsMe = id == loggedId
    User.findById(id) match {
      case None => NotFound
      case Some(user) => Ok(views.html.profile.index(user, itsMe))
    }
  }

}

//TODO: add pjax for all anchor not POST
//TODO: add logout menu when logged in, as POST request (ensure Form avoids fake request)
//TODO: check selected menu at top is the correct one (does jquery onload works fine with pjax request?)
//TODO: add twitter login option
//TODO: add google openid login option < no more, this should work for initial release
//TODO: create CRUD page for admins to manage users (form + edit/update/remove) < see computer demo for pagination, edit, etc
//TODO: create pages to manage projects