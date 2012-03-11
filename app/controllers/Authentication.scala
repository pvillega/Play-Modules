package controllers

import play.api.libs.ws.WS
import play.api.libs.concurrent.Promise
import play.api.libs.ws
import play.api.mvc.{AnyContent, Action, Controller}
import models.User
import play.api.i18n.Messages
import play.api.{Configuration, Logger, Play}


/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 26/02/12
 * Time: 20:49
 * Manages authentication for the application
 */

object Authentication extends Controller {

  def index() = Action {
    implicit request =>
      Logger.info("Authentication.index called")
      Ok(views.html.authentication.index())
  }

  // GitHub connection settings
  val githubClientId = Play.current.configuration.getString("github.clientId").getOrElse("")
  val githubSecret = Play.current.configuration.getString("github.secret").getOrElse("")
  val githubAuthorizeURL = Play.current.configuration.getString("github.authorizeURL").getOrElse("")
  val githubGetTokenURL = Play.current.configuration.getString("github.getTokenURL").getOrElse("")
  val githubGetUser = Play.current.configuration.getString("github.getUser").getOrElse("")

  /**
   * Processes github Authentication
   */
  def githubAuth() = Action {
    implicit request =>
        Logger.info("Authentication.githubAuth called with request %s".format(request))
        //see if we got an answer from github
        val code = request.queryString.get("code")  //code received
        val error = request.queryString.get("error") //error: user denied

        //if we got an error code, redirect to main, else proceed with authentication in github
        error.map { message =>
          Logger.warn("Authentication.githubAuth the request was reject with an error message by github: %s".format(message))
          Redirect("/").flashing("warning" -> Messages("login.backend.cancel"))
        }.getOrElse {
          githubAuthRequest(code)
        }

  }

  /**
   * This method contains the logic for the Oauth requests to github to authenticate the user
   * GitHub requires a GET request followed by a POST. After that we get the Oauth token for the user
   * @param tokenCode token received from github on request, if any
   */
  def githubAuthRequest(tokenCode: Option[Seq[String]])(implicit request: play.api.mvc.Request[AnyContent]) = {
    tokenCode.map {
      code =>
        Logger.info("Authentication.githubAuthRequest received auth code from github %s".format(code))

        // we got an auth code, this means we did the GET request and user granted permission to the application, now we do the POST request for the token
        val result: Promise[ws.Response] = {
          val postRequest = WS.url(githubGetTokenURL).withQueryString("client_id" -> githubClientId, "code" -> code(0), "client_secret" -> githubSecret)
          Logger.debug("Authentication.githubAuthRequest preparing post %s".format(postRequest))
          Logger.info("Authentication.githubAuthRequest preparing post to obtain token")
          postRequest.post("")
        }

        //do post asyn to avoid blocking the thread
        Async {
          result.orTimeout("TimeoutConnectGithub", 10000).map {
            retrieve =>
              retrieve.fold(
                response => processGithubResponse(response.body),
                timeout => Redirect("/").flashing("error" -> Messages("login.backend.failed"))
              )
          }
        }

    }.getOrElse {
      //neither code nor error was received, this means we didn't call from github, go to github authorization
      Logger.info("Authentication.githubAuthRequest redirecting to GitHub for authentication")
      Redirect(url = githubAuthorizeURL, queryString = Map("client_id" -> List(githubClientId)))
    }
  }

  /**
   * Processes response from Github when trying to log in
   * At this stage we have the user token, so we want to see if that user is already in the db, create a new user if not, and set the session
   * @param body   body of the response received
   * @param request implicit request object
   */
  private def processGithubResponse(body: String)(implicit request: play.api.mvc.Request[AnyContent]) = {
    val regexp = """access_token=(\w+)(.*)""".r
    body match {
      case regexp(token, rest) => {
        Logger.info("Authentication.processGithubResponse found the access token".format(body))

        // Get user data
        val userData: Promise[ws.Response] = {
          val userRequest = WS.url(githubGetUser).withQueryString("access_token" -> token)
          Logger.info("Authentication.processGithubResponse preparing user request %s".format(userRequest))
          userRequest.get()
        }

        //async to avoid blocking
        Async {
          userData.orTimeout("TimeoutGetUserIdGithub", 10000).map {
            retrieve =>
              retrieve.fold(
                response => {
                  val githubId = (response.json \ "id").as[Int]
                  val name = (response.json \ "name").as[String]
                  val githubUrl = (response.json \ "url").as[String]
                  val bio = (response.json \ "bio").as[String]
                  val avatar = (response.json \ "avatar_url").as[String]
                  val location = (response.json \ "location").as[String]
                  val blog = (response.json \ "blog").as[String]
                  Logger.debug("Received from github: %d, %s, %s, %s, %s, %s, %s".format(githubId, name, githubUrl, bio, avatar, location, blog))

                  //if we have id in db, load user, otherwise create user
                  val (disabled: Boolean, userId: Long) = User.findByGithubId(githubId) match {
                    case Some(user: User) => {
                      val id = user.id.get
                      Logger.info("Authentication.processGithubResponse existing user with id %d logged via Github".format(id))
                      //update last access date
                      User.updateLastAccess(id)
                      (user.disabled, id)
                    }
                    case None => {                      
                      Logger.info("Authentication.processGithubResponse new user %s logged via Github with githubId %d".format(name, githubId))
                      val newUser: User = User(name = name, githubId = Option(githubId), avatar = Option(avatar), location = Option(location), blog = Option(blog), githubUrl = Option(githubUrl), bio = Option(bio))
                      val id = User.create(newUser)
                      (false, id)
                    }
                  }

                  if (disabled) {
                    Logger.warn("Authentication.processGithubResponse logged user %d is DISABLED, FORBID ACCESS".format(userId))
                    Redirect("/").flashing("warning" -> Messages("login.backend.disabled"))
                  } else {
                    Logger.info("Authentication.processGithubResponse logged user %d is not disabled, go to profile".format(userId))
                    //redirect to profile page
                    Redirect("/profile/%d".format(userId)).flashing("success" -> Messages("login.backend.welcome", name)).withSession("userId" -> userId.toString)
                  }


                },
                timeout => Redirect("/").flashing("error" -> Messages("login.backend.failed"))
              )
          }
        }
      }
      case _ => {
        Logger.warn("Authentication.processGithubResponse couldn't find the access token for body %s".format(body))
        Redirect("/").flashing("error" -> Messages("login.backend.failed"))
      }
    }
  }

  /**
   * Removes user session and redirects to home page
   */
  def logout() = Action{ implicit request =>
    Redirect("/").flashing("info" -> Messages("login.backend.logout")).withNewSession
  }

}
