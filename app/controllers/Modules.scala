package controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.Logger
import play.api.i18n.Messages
import play.api.mvc.{Action, Controller}
import play.libs.Json._
import models.{Release, Module}
import play.api.libs.json._
import java.util.Date
import anorm.{Id, NotAssigned}

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
      "tags" -> list(nonEmptyText verifying pattern("""[\w. ]+""".r)),
      "description" -> optional (text)
    ) {
      (id, name, version, url, tags, description) => Module(name = name, version = version, url = url, description = description, tags = tags)
    }{
      mod: Module => Some(mod.id, mod.name, mod.version, mod.url, mod.tags, mod.description)
    }
  )

  // form to edit release
  val releaseForm: Form[Release] = Form(
    mapping(
      "id" -> ignored(NotAssigned: anorm.Pk[Long]),
      "name" -> nonEmptyText,
      "description" -> optional (text)
    ) {
      (id, name, description) => Release(name = name, description = description)
    }{
      release: Release => Some(release.id, release.name, release.description)
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
   * @param showReleases if true the releases tab is active by default
   */
  def editModule(id: Long, showReleases: Boolean = false) = Authenticated {
    implicit request =>
      Logger.info("Modules.editModule accessed by user %d to edit module[%d]".format(request.user.id.get, id))
      Module.findById(id) match {
        case Some(mod) if mod.author == request.user.id.get =>
          Ok(views.html.modules.editModule(modForm.fill(mod), mod.id.get, Release.fetchRecentReleases(mod.id.get), showReleases)
        )
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
          BadRequest(views.html.modules.editModule(errors, id, Release.fetchRecentReleases(id), false))
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
   *
   * @param id the id of the module to show
   */
  def viewModule(id: Long) =  Action {
    implicit request =>
      Logger.info("Modules.viewModule accessed to view module[%d]".format(id))
      Module.findByIdWithVersion(id) match {
        case Some(mod) => {
          val vote = Module.getUserVote(request.session.get("userId"), mod.id)
          Ok(views.html.modules.viewModule(mod, vote, Release.fetchRecentReleases(id)))
        }
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
  def voteModule(id: Long, vote: Int, oldVote: Option[Int]) = Authenticated {
    implicit request =>
      Logger.info("Modules.voteModule accessed by user %d to add vote[%d] to module[%d]".format(request.user.id.get, vote, id))
      Module.findById(id) match {
        case Some(mod) => {
          Module.vote(request.user.id.get, id, vote, oldVote.getOrElse(0))
          Redirect(routes.Modules.viewModule(id)).flashing("success" -> Messages("mods.voted"))
        }
        case _ => {
          Logger.warn("Modules.voteModule can't find the module[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Shows who has voted the current module
   *
   * @param id the id of the module from which we want the votes
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   */
  def viewVotes(id: Long, page: Int, orderBy: Int) = Action {
    implicit request =>
      Logger.info("Modules.viewVotes accessed for module[%d]".format(id))
      Module.findById(id) match {
        case Some(mod) => {
          val votes = Module.listOfVotes(page = page, orderBy = orderBy, pageSize = 20, modid = id)
          Ok(views.html.modules.viewVotes(id, votes, orderBy))
        }
        case _ => {
          Logger.warn("Modules.viewVotes can't find the module[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Retrieves a list of Releases linked to the given module. Used on Ajax requests
   * @param modid id of the module that owns the releases
   * @param start we want to start from this release
   */
  def fetchReleases(modid: Long, start: Int) = Action {
    implicit request =>

      //required implicit conversion for Json formatting
      implicit object releaseToJson extends Format[Release] {
        val format = new java.text.SimpleDateFormat("dd-MM-yyyy")

        def writes(o: Release): JsValue = JsObject(
          List(
            "id" -> JsNumber(o.id.get),
            "name" -> JsString(o.name),
            "created" -> JsString(format.format(o.created))
          )
        )
        //not used, added for compliance
        def reads(json: JsValue): Release = Release(
          id = Id((json \ "name").as[Long]),
          name = (json \ "name").as[String],
          created = format.parse((json \ "created").as[String])
        )
      }
      Logger.info("Modules.fetchReleases accessed for module[%d] from position[%d]".format(modid, start))
      Ok(Json.toJson(
        Release.fetchRecentReleases(modid = modid, from = start))).as("application/json")
  }

  /**
   * Adds a release to the current Module
   * @param modid the id of the module that owns the release
   */
  def addRelease(modid: Long) = Authenticated {
    implicit request =>
      Logger.info("Modules.addRelease accessed by user %d to create a new release for module[%d]".format(request.user.id.get, modid))
      Ok(views.html.modules.addRelease(releaseForm, modid))
  }

  /**
   * Saves the release to the given module
   * @param modid the id of the module that owns the release
   */
  def saveRelease(modid: Long) = Authenticated {
    implicit request =>
      releaseForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.warn("Modules.saveRelease errors while saving release [%s]".format(errors))
          BadRequest(views.html.modules.addRelease(errors, modid))
        },
        // We got a valid User value, update
        release => {
          Module.findById(modid) match {
            case Some(mod) if mod.author == request.user.id.get => {
              Logger.info("Modules.saveRelease saving new release [%s]".format(release))
              val newRelease = Release.create(release, modid)
              Redirect(routes.Modules.editRelease(newRelease.id.get, modid)).flashing("success" -> Messages("release.saved"))
            }
            case _ => {
              Logger.warn("Modules.saveRelease can't find the module[%d]".format(modid))
              NotFound(views.html.errors.error404(request.path)(request))
            }
          }
        }
      )
  }

  /**
   * Edits the selected release
   *
   * @param id the id of the release to edit
   * @param modid the id of the module that owns the release
   */
  def editRelease(id: Long, modid: Long) = Authenticated {
    implicit request =>
      Logger.info("Modules.editRelease accessed by user %d to edit release [%d] for module[%d]".format(request.user.id.get, id, modid))
      Release.findById(id) match {
        case Some(release) if release.moduleid == modid => Ok(views.html.modules.editRelease(releaseForm.fill(release), modid, release.id.get))
        case _ => {
          Logger.warn("Modules.editRelease can't find release [%d] for module[%d]".format(id, modid))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Saves the changes to the release
   *
   * @param id the id of the release we modified
   * @param modid the id of the module that owns the release
   */
  def updateRelease(id: Long, modid: Long) = Authenticated {
    implicit request =>
      releaseForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.warn("Modules.updateRelease errors while updating release %d [%s]".format(id, errors))
          BadRequest(views.html.modules.editRelease(errors, id, modid))
        },
        // We got a valid value, update
        release => {
          Logger.info("Modules.updateRelease updating release %d [%s]".format(id, release))
          Release.update(id, release, modid, request.user.id.get)
          Redirect(routes.Modules.editRelease(id, modid)).flashing("success" -> Messages("release.updated"))
        }
      )
  }

  /**
   * Deletes the given release
   *
   * @param id the id of the release to remove
   * @param modid the id of the module that owns the release
   */
  def deleteRelease(id: Long, modid: Long) = Authenticated {
    implicit request =>
      Logger.info("Modules.deleteRelease accessed by user %d to delete release[%d] from module[%d]".format(request.user.id.get, id, modid))
      Release.findById(id) match {
        case Some(release) => {
          Release.delete(id, modid, request.user.id.get)
          Redirect(routes.Modules.editModule(modid, true)).flashing("success" -> Messages("release.deleted"))
        }
        case _ => {
          Logger.warn("Modules.deleteModule can't find the release[%d] in module[%d]".format(id, modid))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Shows the selected release
   *
   * @param id the id of the release
   * @param modid the id of the module that owns the release
   */
  def viewRelease(id: Long, modid: Long) =  Action {
    implicit request =>
      Logger.info("Modules.viewRelease accessed to view release[%d] of module[%d]".format(id, modid))
      Release.findById(id) match {
        case Some(release) => {
          Ok(views.html.modules.viewRelease(release, modid))
        }
        case _ => {
          Logger.warn("Modules.viewRelease can't find the release[%d] of module[%d]".format(id, modid))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

}
