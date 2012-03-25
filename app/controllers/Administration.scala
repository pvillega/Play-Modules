package controllers

import play.api.mvc.Controller
import play.api.data.Form
import play.api.data.Forms._
import play.Logger
import play.api.i18n.Messages
import models.{Version, User}
import anorm.NotAssigned

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
      "id" -> ignored(NotAssigned: anorm.Pk[Long]),
      "name" -> nonEmptyText,
      "githubId" -> optional(longNumber verifying( _ > 0)),
      "twitterId" -> optional(longNumber verifying( _ > 0)),
      "googleId" -> optional(text),
      "disabled" -> boolean,
      "admin" -> boolean,
      "created" -> date,
      "lastAccess" -> date,
      "avatar" -> optional(text),
      "url" -> optional(text),
      "location" -> optional(text),
      "bio" -> optional(text)
    ) (User.apply)(User.unapply))

  // form to edit versions
  val versionForm: Form[Version] = Form(
    mapping(
      "id" -> ignored(NotAssigned: anorm.Pk[Long]),
      "name" -> nonEmptyText,
      "parent" -> optional(longNumber verifying( _ > 0))
    ) (Version.apply)(Version.unapply))

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
  def updateUser(id: Long) = IsAdmin {
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

  /**
   * Versions administration screen
   */
  def versions() = IsAdmin {
    implicit request =>
      Logger.info("Administration.versions accessed by user %d".format(request.user.id.get))
      Redirect(routes.Administration.listVersions())
  }

  /**
   * Display the paginated list of versions.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param filter Filter applied on user names
   */
  def listVersions(page: Int, orderBy: Int, filter: String) = IsAdmin {
    implicit request =>
      Logger.info("Administration.listVersions accessed by user %d with params page[%d] orderBy[%d] filter[%s]".format(request.user.id.get, page, orderBy, filter))
      Ok(views.html.administration.versions(
        Version.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%")),
        orderBy,
        filter
      ))
  }

  /**
   * Edits the selected version
   *
   * @param id the id of the version to edit
   */
  def editVersion(id: Long) = IsAdmin {
    implicit request =>
      Logger.info("Administration.editVersion accessed by user %d to edit version[%d]".format(request.user.id.get, id))
      Version.findById(id) match {
        case Some(version) => Ok(views.html.administration.editVersion(versionForm.fill(version), version.id.get))
        case _ => NotFound(views.html.errors.error404(request.path)(request))
      }
  }

  /**
   * Creates a new version
   *
   */
  def createVersion() = IsAdmin {
    implicit request =>
      Logger.info("Administration.createVersion accessed by user %d to create a new version".format(request.user.id.get))
      Ok(views.html.administration.createVersion(versionForm))
  }

  /**
   * Saves the changes to the version
   *
   * @param id the id of the version we modified
   */
  def updateVersion(id: Long) = IsAdmin {
    implicit request =>
      versionForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => BadRequest(views.html.administration.editVersion(errors, id)),
        // We got a valid User value, update
        version => {
          Version.update(id, version)
          Redirect(routes.Administration.editVersion(id)).flashing("success" -> Messages("versionadmin.updated"))
        }
      )
  }

  /**
   * Stores the new version
   */
  def saveVersion() = IsAdmin {
    implicit request =>
      versionForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => BadRequest(views.html.administration.createVersion(errors)),
        // We got a valid User value, update
        version => {
          val newVersion = Version.create(version)
          Redirect(routes.Administration.editVersion(newVersion.id.get)).flashing("success" -> Messages("versionadmin.saved"))
        }
      )
  }

}
