package controllers

import play.api.data.Form
import play.api.data.Forms._
import anorm.NotAssigned
import play.Logger
import models.{Demo, Tag}
import play.api.i18n.Messages
import scala.Int
import play.api.mvc.{QueryStringBindable, Action, Controller}

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 25/03/12
 * Time: 17:57
 * Controller for demo management
 */
object Demos extends Controller with Secured {

  // form to edit demos
  val demoForm: Form[Demo] = Form(
    mapping(
      "id" -> ignored(NotAssigned: anorm.Pk[Long]),
      "name" -> nonEmptyText,
      "version" -> (longNumber verifying( _ > 0)),
      "codeurl" -> text, // no regexp validation, see: http://stackoverflow.com/questions/3058138/is-it-safe-to-validate-a-url-with-a-regexp
      "tags" -> list(nonEmptyText),
      "demourl" -> optional (text),
      "description" -> optional (text)
    ) {
        (id, name, version, codeurl, tags, demourl, description) => Demo(name = name, version = version, codeurl = codeurl, demourl = demourl, description = description, tags = tags)
    }{
        demo: Demo => Some(demo.id, demo.name, demo.version, demo.codeurl, demo.tags, demo.demourl, demo.description)
    }
  )

  /**
   * Creates a new demo
   *
   */
  def createDemo() = Authenticated {
    implicit request =>
      Logger.info("Demos.createDemo accessed by user %d to create a new demo".format(request.user.id.get))
      Ok(views.html.demos.createDemo(demoForm))
  }

  /**
   * Stores the new demo
   */
  def saveDemo() = Authenticated {
    implicit request =>
      Logger.info("Demos.saveDemo accessed by user %d to save a new demo".format(request.user.id.get))
      demoForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.error("Demos.saveDemo errors while saving Demo: %s".format(errors))
          BadRequest(views.html.demos.createDemo(errors))
        },
        // We got a valid value, update
        demo => {
          Logger.info("Demos.saveDemo saving new demo with details [%s]".format(demo))
          val newDemo = Demo.create(demo, request.user.id.get)
          Redirect(routes.Demos.editDemo(newDemo.id.get)).flashing("success" -> Messages("demos.saved"))
        }
      )
  }

  /**
   * Edits the selected demo
   *
   * @param id the id of the demo to edit
   */
  def editDemo(id: Long) = Authenticated {
    implicit request =>
      Logger.info("Demos.editDemo accessed by user %d to edit demo[%d]".format(request.user.id.get, id))
      Demo.findById(id) match {
        case Some(demo) if demo.author == request.user.id.get => Ok(views.html.demos.editDemo(demoForm.fill(demo), demo.id.get))
        case _ => {
          Logger.warn("Demos.editDemo can't find the demo[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Saves the changes to the demo
   *
   * @param id the id of the demo we modified
   */
  def updateDemo(id: Long) = Authenticated {
    implicit request =>
      Logger.info("Demos.updateDemo accessed by user %d to update demo %d".format(request.user.id.get, id))
      demoForm.bindFromRequest.fold(
        // Form has errors, redisplay it
        errors => {
          Logger.error("Demos.updateDemo Errors while updating Demo %d: %s".format(id, errors))
          BadRequest(views.html.demos.editDemo(errors, id))
        },
        // We got a valid User value, update
        demo => {
          Logger.info("Demos.updateDemo Updating demo %d with details [%s]".format(id, demo))
          Demo.update(id, request.user.id.get, demo)
          Redirect(routes.Demos.editDemo(id)).flashing("success" -> Messages("demos.updated"))
        }
      )
  }

  /**
   * Deletes the given demo
   *
   * @param id the di of the demo to remove
   */
  def deleteDemo(id: Long) = Authenticated {
    implicit request =>
      Logger.info("Demos.deleteDemo accessed by user %d to delete demo[%d]".format(request.user.id.get, id))
      Demo.findById(id) match {
        case Some(demo) if demo.author == request.user.id.get => {
          Demo.delete(id, request.user.id.get)
          Redirect(routes.Profile.index(request.user.id.get)).flashing("success" -> Messages("demos.deleted"))
        }
        case _ => {
          Logger.warn("Demos.deleteDemo can't find the demo[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }


  /**
   * Display the paginated list of demos.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param nameFilter Filter applied on demo names
   * @param versionFilter filter applied on version
   * @param tagFilter filter applied on tags
   */
  def listDemos(page: Int, orderBy: Int, nameFilter: String, versionFilter : Long, tagFilter: List[String]) = Action {
    implicit request =>
      Logger.info("Demos.listDemos accessed params page[%d] orderBy[%d] filter[%s | %d | %s]".format(page, orderBy, nameFilter, versionFilter, tagFilter))
      Ok(views.html.demos.listDemos(
        Demo.list(page = page, orderBy = orderBy, nameFilter = ("%" + nameFilter + "%"), versionFilter = versionFilter, tagFilter = tagFilter),
        orderBy,
        nameFilter,
        versionFilter,
        tagFilter
      ))
  }


  /**
   * Shows the selected demo
   * @param id the id of the demo to show
   */
  def viewDemo(id: Long) =  Action {
    implicit request =>
      Logger.info("Demos.viewDemo accessed to view demo[%d]".format(id))
      Demo.findByIdWithVersion(id) match {
        case Some(demo) => Ok(views.html.demos.viewDemo(demo))
        case _ => {
          Logger.warn("Demos.viewDemo can't find the demo[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }

  /**
   * Records a vote by the logged in user against a Demo 
   * @param id demo we are voting for
   * @param vote the vote given (+1/-1)
   * @param oldVote the previous vote given (+1/-1/0)
   */
  def voteDemo(id: Long, vote: Int, oldVote: Int) = Authenticated {
    implicit request =>
      Logger.info("Demos.voteDemo accessed by user %d to add vote[%d] to demo[%d]".format(request.user.id.get, vote, id))
      Demo.findById(id) match {
        case Some(demo) => {
          Demo.vote(request.user.id.get, id, vote, oldVote)
          Redirect(routes.Demos.viewDemo(id)).flashing("success" -> Messages("demos.voted"))
        }
        case _ => {
          Logger.warn("Demos.voteDemo can't find the demo[%d]".format(id))
          NotFound(views.html.errors.error404(request.path)(request))
        }
      }
  }


}
