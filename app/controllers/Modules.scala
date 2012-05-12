package controllers

import play.api.data.Form
import models.Module
import play.api.data.Forms._
import anorm.NotAssigned
import play.Logger
import play.api.i18n.Messages
import play.api.mvc.{Action, Controller}

/**
 * Created with IntelliJ IDEA.
 * User: pvillega
 * Date: 12/05/12
 * Time: 18:44
 * Controller for Modules management
 */

object Modules extends Controller with Secured {

  // form to edit modules
  val modForm: Form[Module] = Form(
    mapping(
      "id" -> ignored(NotAssigned: anorm.Pk[Long]),
      "name" -> nonEmptyText,
      "version" -> (longNumber verifying( _ > 0)),
      "url" -> text, // no regexp validation, see: http://stackoverflow.com/questions/3058138/is-it-safe-to-validate-a-url-with-a-regexp
      "tags" -> list(nonEmptyText),
      "description" -> optional (text)
    ) {
      (id, name, version, url, tags, description) => Module(name = name, version = version, url = url, description = description, tags = tags)
    }{
      mod: Module => Some(mod.id, mod.name, mod.version, mod.url, mod.tags, mod.description)
    }
  )

  /**
   * Creates a new Module
   *
   */
  def createModule() = Authenticated {
    implicit request =>
      Logger.info("Modules.createModule accessed by user %d to create a new module".format(request.user.id.get))
      Ok(views.html.modules.createModule(modForm))
  }

  /**
   * Stores the new module
   */
  def saveModule() = Authenticated {
    implicit request =>
      Logger.info("Modules.saveModule accessed by user %d to save a new module".format(request.user.id.get))
      modForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.error("Modules.saveModule errors while saving Module: %s".format(errors))
          BadRequest(views.html.modules.createModule(errors))
        },
        // We got a valid value, update
        mod => {
          Logger.info("Modules.saveModule saving new Module with details [%s]".format(mod))
          val newModule = Module.create(mod, request.user.id.get)
          Redirect(routes.Modules.editModule(newModule.id.get)).flashing("success" -> Messages("mods.saved"))
        }
      )
  }

  /**
   * Edits the selected module
   *
   * @param id the id of the module to edit
   */
  def editModule(id: Long) = Authenticated {
    implicit request =>
      Logger.info("Modules.editModule accessed by user %d to edit module[%d]".format(request.user.id.get, id))
      Module.findById(id) match {
        case Some(mod) if mod.author == request.user.id.get => Ok(views.html.modules.editModule(modForm.fill(mod), mod.id.get))
        case _ => {
          Logger.warn("Modules.editModule can't find the module[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Saves the changes to the module
   *
   * @param id the id of the module we modified
   */
  def updateModule(id: Long) = Authenticated {
    implicit request =>
      Logger.info("Modules.updateModule accessed by user %d to update module %d".format(request.user.id.get, id))
      modForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.error("Modules.updateModule Errors while updating Module %d: %s".format(id, errors))
          BadRequest(views.html.modules.editModule(errors, id))
        },
        // We got a valid value, update
        mod => {
          Logger.info("Demos.updateDemo Updating module %d with details [%s]".format(id, mod))
          Module.update(id, request.user.id.get, mod)
          Redirect(routes.Modules.editModule(id)).flashing("success" -> Messages("mods.updated"))
        }
      )
  }

  /**
   * Deletes the given module
   *
   * @param id the id of the module to remove
   */
  def deleteModule(id: Long) = Authenticated {
    implicit request =>
      Logger.info("Modules.deleteModule accessed by user %d to delete module[%d]".format(request.user.id.get, id))
      Module.findById(id) match {
        case Some(mod) if mod.author == request.user.id.get => {
          Module.delete(id, request.user.id.get)
          Redirect(routes.Profile.index(request.user.id.get)).flashing("success" -> Messages("mods.deleted"))
        }
        case _ => {
          Logger.warn("Modules.deleteModule can't find the module[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }


  /**
   * Display the paginated list of modules.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param nameFilter Filter applied on module names
   * @param versionFilter filter applied on version
   * @param tagFilter filter applied on tags
   */
  def listModules(page: Int, orderBy: Int, nameFilter: String, versionFilter : Long, tagFilter: List[String]) = Action {
    implicit request =>
      Logger.info("Modules.listModules accessed params page[%d] orderBy[%d] filter[%s | %d | %s]".format(page, orderBy, nameFilter, versionFilter, tagFilter))
      Ok(views.html.modules.listModules(
        Module.list(page = page, orderBy = orderBy, nameFilter = ("%" + nameFilter + "%"), versionFilter = versionFilter, tagFilter = tagFilter),
        orderBy,
        nameFilter,
        versionFilter,
        tagFilter
      ))
  }


  /**
   * Shows the selected module
   * @param id the id of the module to show
   */
  def viewModule(id: Long) =  Action {
    implicit request =>
      Logger.info("Modules.viewModule accessed to view module[%d]".format(id))
      Module.findByIdWithVersion(id) match {
        case Some(mod) => Ok(views.html.modules.viewModule(mod))
        case _ => {
          Logger.warn("Modules.viewModule can't find the module[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Records a vote by the logged in user against a Module
   * @param id module we are voting for
   * @param vote the vote given (+1/-1)
   * @param oldVote the previous vote given (+1/-1/0)
   */
  def voteModule(id: Long, vote: Int, oldVote: Int) = Authenticated {
    implicit request =>
      Logger.info("Modules.voteModule accessed by user %d to add vote[%d] to module[%d]".format(request.user.id.get, vote, id))
      Module.findById(id) match {
        case Some(mod) => {
          Module.vote(request.user.id.get, id, vote, oldVote)
          Redirect(routes.Modules.viewModule(id)).flashing("success" -> Messages("mods.voted"))
        }
        case _ => {
          Logger.warn("Modules.voteModule can't find the module[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }


}
