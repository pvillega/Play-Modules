package controllers

import play.api.mvc.Controller
import play.api.data.Form
import play.api.data.Forms._
import play.Logger
import play.api.i18n.Messages
import anorm.NotAssigned
import models.{TagMerge, Tag, Version, User}

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
      "bio" -> optional(text),
      "location" -> optional(text)
    ) (User.apply)(User.unapply))

  // form to edit versions
  val versionForm: Form[Version] = Form(
    mapping(
      "id" -> ignored(NotAssigned: anorm.Pk[Long]),
      "name" -> nonEmptyText,
      "parent" -> optional(longNumber verifying( _ > 0))
    ) (Version.apply)(Version.unapply))

  // form to edit tags
  val tagForm: Form[Tag] = Form(
    mapping(
      "id" -> ignored(NotAssigned: anorm.Pk[Long]),
      "name" -> nonEmptyText
    ) (Tag.apply)(Tag.unapply))

  //form used to merge tags
  val mergetagForm: Form[TagMerge] = Form(
    mapping(
    "sourceid" -> longNumber,
    "tags" -> list(nonEmptyText)
    )(TagMerge.apply) (TagMerge.unapply)
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
        case _ => {
          Logger.warn("Administration.editUser can't find user [%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
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
        errors => {
          Logger.warn("Administration.updateUser errors while updating user %d [%s]".format(id, errors))
          BadRequest(views.html.administration.editUser(errors, id))
        },
        // We got a valid User value, update
        user => {
          Logger.info("Administration.updateUser updating user %d [%s]".format(id, user))
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
        case _ => {
          Logger.warn("Administration.editVersion can't find version [%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
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
        errors => {
          Logger.warn("Administration.updateVersion errors while updating version %d [%s]".format(id, errors))
          BadRequest(views.html.administration.editVersion(errors, id))
        },
        // We got a valid User value, update
        version => {
          Logger.info("Administration.updateVersion updating version %d [%s]".format(id, version))
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
        errors => {
          Logger.warn("Administration.saveVersion errors while saving version [%s]".format(errors))
          BadRequest(views.html.administration.createVersion(errors))
        },
        // We got a valid User value, update
        version => {
          Logger.info("Administration.saveVersion saving new version [%s]".format(version))
          val newVersion = Version.create(version)
          Redirect(routes.Administration.editVersion(newVersion.id.get)).flashing("success" -> Messages("versionadmin.saved"))
        }
      )
  }

  /**
   * Tags administration screen
   */
  def tags() = IsAdmin {
    implicit request =>
      Logger.info("Administration.tags accessed by user %d".format(request.user.id.get))
      Redirect(routes.Administration.listTags())
  }

  /**
   * Display the paginated list of tags.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param filter Filter applied on user names
   */
  def listTags(page: Int, orderBy: Int, filter: String) = IsAdmin {
    implicit request =>
      Logger.info("Administration.listTags accessed by user %d with params page[%d] orderBy[%d] filter[%s]".format(request.user.id.get, page, orderBy, filter))
      Ok(views.html.administration.tags(
        Tag.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%")),
        orderBy,
        filter
      ))
  }

  /**
   * Edits the selected tag
   *
   * @param id the id of the tag to edit
   */
  def editTag(id: Long) = IsAdmin {
    implicit request =>
      Logger.info("Administration.editTag accessed by user %d to edit tag[%d]".format(request.user.id.get, id))
      Tag.findById(id) match {
        case Some(tag) => Ok(views.html.administration.editTag(tagForm.fill(tag), tag.id.get))
        case _ => {
          Logger.warn("Administration.editTag can't find tag [%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Creates a new tag
   */
  def createTag() = IsAdmin {
    implicit request =>
      Logger.info("Administration.createTag accessed by user %d to create a new tag".format(request.user.id.get))
      Ok(views.html.administration.createTag(tagForm))
  }

  /**
   * Saves the changes to the tag
   *
   * @param id the id of the tag we modified
   */
  def updateTag(id: Long) = IsAdmin {
    implicit request =>
      tagForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.warn("Administration.updateTag errors while updating tag %d [%s]".format(id, errors))
          BadRequest(views.html.administration.editTag(errors, id))
        },
        // We got a valid value, update
        tag => {
          Logger.info("Administration.updateTag updating tag %d [%s]".format(id, tag))
          Tag.update(id, tag)
          Redirect(routes.Administration.editTag(id)).flashing("success" -> Messages("tagadmin.updated"))
        }
      )
  }

  /**
   * Stores the new Tag
   */
  def saveTag() = IsAdmin {
    implicit request =>
      tagForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.warn("Administration.saveTag errors while saving tag [%s]".format(errors))
          BadRequest(views.html.administration.createTag(errors))
        },
        // We got a valid value, update
        tag => {
          Logger.info("Administration.saveTag saving new tag [%s]".format(tag))
          val newTag = Tag.create(tag)
          Redirect(routes.Administration.editTag(newTag.id.get)).flashing("success" -> Messages("tagadmin.saved"))
        }
      )
  }

  /**
   * Deletes the given Tag
   * @param id  id of the tag to remove
   */
  def deleteTag(id: Long) = IsAdmin {
    implicit request =>
      Logger.info("Administration.deleteTag user[%d] removing tag [%d]".format(request.user.id.get, id))
      Tag.delete(id)
      Redirect(routes.Administration.listTags())
  }

  /**
   * Shows the merge tag screen
   */
  def mergeTags() = IsAdmin {
    implicit request =>
      Logger.info("Administration.mergeTags accessed by user %d".format(request.user.id.get))
      Ok(views.html.administration.mergeTags(mergetagForm))
  }

  /**
   * Merges the given tags
   */
  def doMergeTags() = IsAdmin {
    implicit request =>
      mergetagForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.warn("Administration.mergeTags errors while merging tags [%s]".format(errors))
          BadRequest(views.html.administration.mergeTags(errors))
        },
        // We got a valid value, update
        tagmerge => {
          Logger.info("Administration.mergeTags merging tag %d with [%s]".format(tagmerge.sourceid, tagmerge.tags))
          Tag.merge(tagmerge)
          Redirect(routes.Administration.listTags()).flashing("success" -> Messages("tagadmin.merged"))
        }
      )
  }


}

//TODO: add management of demos/projects as admin