package controllers

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.iteratee._


object Application extends Controller with Secured {
  
  def index = Action { implicit request =>
    Logger.info("Application.index accessed")
    Ok(views.html.index())
  }

  def browse() = IsAuthenticated { userId => implicit request =>
    Ok("It's %s".format(userId))
  }                     

}

/**
  * Provide security features
 */


/**
 * Trait to validate authenticated users, based on
 */
trait Secured {

  /**
   * Retrieve the connected user id from the session
   * It returns an Option with the id if found
   */
  private def getUserId(request: RequestHeader) : Option[String] = request.session.get("userId")

  /**
   * Redirect to NotFound if the user is not authorized.
   * It returns a Result to redirect the user to the "not authenticated" destination of choice
   */
  private def onUnauthorized(request: RequestHeader) : Result = NotFound(views.html.errors.error404(request.path)(request)).withNewSession


  /**
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Result) = Authenticated(getUserId, onUnauthorized) { user =>
    Action(request => f(user)(request))
  }

  /**
   * Based on the original code of Security class, with minor changes and comments to explain behaviour
   *
   * Wraps another action, allowing only authenticated HTTP requests.
   *
   * The user name is retrieved from the session cookie, and added to the HTTP request’s
   * `username` attribute.
   *
   * For example:
   * {{{
   * Authenticated {
   * Action { request =>
   * Ok(request.username.map("Hello " + _))
   * }
   * }
   * }}}
   *
   * @tparam A the type of the request body
   * @param obtainUserId function used to retrieve the user id from the request header - the default is to read from session cookie
   * @param onUnauthorized function used to generate alternative result if the user is not authenticated - the default is a simple 401 page
   * @param action the action to wrap
   */
  def Authenticated[A](
    obtainUserId: RequestHeader => Option[String],
    onUnauthorized: RequestHeader => Result)(action: String => Action[A]): Action[(Action[A], A)] = {

    // define a helper body parser to intercept call before processing and decide if we can continue or not
    val authenticatedBodyParser = BodyParser { requestHeader =>
      //use provided function to recover user id from the request header
      obtainUserId(requestHeader).map { userId =>
        //we have user id, use it in provided action param to obtain: Action(request => f(user)(request))
        val innerAction = action(userId)

        // execute body parser of the action, which returns a BodyParser.
        // BodyParser is a function that returns an Iteratee[scala.Array[scala.Byte], scala.Either[play.api.mvc.Result, A]]. MapDone
        // sets body to an Either[Result, A]. Whe choose right (success, which means A) and return a Tuple[Action[A], A]
        innerAction.parser(requestHeader).mapDone { body =>
          body.right.map(innerBody => (innerAction, innerBody))
        }
      }.getOrElse {
        // no user id available, deny access. We returns a Left (failure) on the result of the Iterator,
        // which means that we obtain a Result (the unauthorized in here) to stop the request
        Done(Left(onUnauthorized(requestHeader)), Input.Empty)
      }
    }

    // Call Action giving the body parser defined above. The parser will run first and if the user is authenticated
    // we will execute  the body
    Action(authenticatedBodyParser) { implicit request =>
      // obtain the Tuple[Action[A], A] and assign to variables
      val (innerAction, innerBody) = request.body
      // execute the Action received giving the innerBody as request object (we use map to replace the Request[(Action[A],A)} into Request[A], see method definition)
      innerAction(request.map(_ => innerBody))
    }

  }

}
