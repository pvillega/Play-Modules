package controllers

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.iteratee._
import play.api.mvc.BodyParsers._
import models.User


object Application extends Controller with Secured {

  def index = Action {
    implicit request =>
      Logger.info("Application.index accessed")
      Ok(views.html.index())
  }

  def browse() = TODO

}

//Extension of request object to include the current logged in user
case class AuthenticatedRequest[A](val user: models.User, val request: Request[A]) extends WrappedRequest(request)

/**
 * Trait to validate authenticated users, based on Play secured trait
 */
trait Secured {

  /**
   * Redirect to NotFound if the user is not authorized.
   * It returns a Result to redirect the user to the "not authenticated" destination of choice
   */
  private def onUnauthorized(request: RequestHeader): Result = {
    Logger.warn("Unauthorized request %s".format(request))
    NotFound(views.html.errors.error404(request.path)(request)).withNewSession
  }

  /**
   * Verifies the user has authenticated
   * @param p the body parser of this action
   * @param f the action to execute
   * @tparam A content type
   * @return Result of the action
   */
  def Authenticated[A](p: BodyParser[A])(f: AuthenticatedRequest[A] => Result) = {
    Action(p) {
      implicit request =>
        request.session.get("userId").flatMap(u => User.findById(u.toLong)).map {
          user =>
            f(AuthenticatedRequest(user, request))
        }.getOrElse(onUnauthorized(request))
    }
  }

  /**
   * Overloaded method to use the default body parser
   */
  def Authenticated(f: AuthenticatedRequest[AnyContent] => Result): Action[AnyContent] = {
    Authenticated(parse.anyContent)(f)
  }


  /**
   * Checks if the authenticated user is an admin
   * @param p the body parser of this action
   * @param f the action to execute
   * @tparam A content type
   * @return Result of the action
   */
  def IsAdmin[A](p: BodyParser[A])(f: AuthenticatedRequest[A] => Result) = {
    Authenticated(p) { implicit request =>
      if(request.user.admin) {
        f(AuthenticatedRequest(request.user, request))
      }  else {
        onUnauthorized(request)
      }
    }
  }

  /**
   * Overloaded method to use the default body parser
   */
  def IsAdmin(f: AuthenticatedRequest[AnyContent] => Result): Action[AnyContent] = {
    IsAdmin(parse.anyContent)(f)
  }

}
