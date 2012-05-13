package controllers

import play.api.libs.ws
import models.User
import play.api.i18n.Messages
import play.api.{Logger, Play}
import play.api.libs.oauth._
import ws.{Response, WS}
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.openid.OpenID
import play.api.libs.concurrent.{Thrown, Redeemed, Promise}

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 26/02/12
 * Time: 20:49
 * Manages authentication for the application
 */
sealed trait IdService {

  /**
   * Authenticates the user, creating a new entry in the db if required
   * @param user either a new user or an existing user
   */
  def authenticateUser(user: Either[User, User]) :  PlainResult  = {

    //if we have user in db load user, otherwise create user
    val (disabled: Boolean, userId: Long) = user match {
      case Right(u) => {
        val id = u.id.get
        Logger.info("IdService existing user with id %d logged via %s".format(id,this.toString))
        //update last access date
        User.updateLastAccess(id)
        (u.disabled, id)
      }
      case Left(u) => {
        Logger.info("IdService new user %s logged via %s".format(u.name, this.toString))
        val id = User.create(u)
        (false, id)
      }
    }

    if (disabled) {
      Logger.warn("IdService logged user %d is DISABLED, FORBID ACCESS".format(userId))
      Redirect(routes.Application.index).flashing("warning" -> Messages("login.backend.disabled"))
    } else {
      Logger.info("IdService logged user %d is not disabled, go to profile".format(userId))
      //redirect to profile page
      Redirect(routes.Profile.index(userId)).flashing("success" -> Messages("login.backend.welcome")).withSession("userId" -> userId.toString)
    }

  }
}

sealed trait OauthService extends IdService {
  /**
   * We obtain the user from the data received. Left means the user is a new user, Right is an existing user
   */
  def processUser(response: Response): Either[User, User]

}

sealed trait OpenIdService extends IdService {
  /**
   * We obtain the user from the data received. Left means the user is a new user, Right is an existing user
   */
  def processUser(info: play.api.libs.openid.UserInfo) : Either[User, User]

}

case object GitHub extends OauthService {


  override def processUser(response: Response) = {
    val id = (response.json \ "id").as[Int]
    val name = (response.json \ "name").as[String]
    val url = (response.json \ "html_url").as[String]
    val bio = (response.json \ "bio").as[String]
    val location = (response.json \ "location").as[String]
    val avatar = (response.json \ "avatar_url").as[String]
    Logger.debug("Received from github: %d, %s, %s, %s, %s".format(id, name, url, bio, avatar))

    User.findByGithubId(id) match {
      case Some(user) => Right(user)
      case None => Left(User(name = name, githubId = Option(id), avatar = Option(avatar), url = Option(url), bio = Option(bio), location = Option(location)) )
    }
  }
}

case object Twitter extends OauthService {

  override def processUser(response: Response) = {
    val id = (response.json \ "id").as[Int]
    val name = (response.json \ "name").as[String]
    val url = (response.json \ "url").as[String]
    val bio = (response.json \ "description").as[String]
    val avatar = (response.json \ "profile_image_url").as[String]
    Logger.debug("Received from Twitter: %d, %s, %s, %s, %s".format(id, name, url, bio, avatar))

    User.findByTwitterId(id) match {
      case Some(user) => Right(user)
      case None => Left(User(name = name, twitterId = Option(id), avatar = Option(avatar), url = Option(url), bio = Option(bio)))
    }
  }
}

case object Google extends OpenIdService {

  override def processUser(info: play.api.libs.openid.UserInfo) = {
    val id = info.id
    val name = info.attributes.getOrElse("fullname", "")
    val firstName = info.attributes.getOrElse("firstname", "")
    val lastName = info.attributes.getOrElse("lastname", "")
    val country = info.attributes.getOrElse("country", "")
    Logger.debug("Received from Google: %s".format(info))

    User.findByGoogleId(id) match {
      case Some(user) => Right(user)
      case None => {
        val username = if (name.isEmpty) firstName +" " + lastName else name
        Left(User(name = username, googleId = Option(id), location = Option(country)) )
      }
    }
  }
}


object Authentication extends Controller with Secured {

  def index() = Action {
    implicit request =>
      Logger.info("Authentication.index called")
      Ok(views.html.authentication.index())
  }


  /**
   * Receives a WS promise (from a call specific to a service) and tries to authenticate the user
   * @param userData a WS promise (from a call specific to a service)
   * @param service the service we used to authenticate
   */
  def retrieveUserDataFromService(userData: Promise[ws.Response], service: IdService) = {
    //async to avoid blocking
    Async {
      userData.orTimeout("TimeoutGetUserDataFromService", 10000).map {
        retrieve =>
          retrieve.fold(
            service match {
              case oauth: OauthService => response => oauth.authenticateUser( oauth.processUser(response) )
              case _ => response => Redirect(routes.Application.index).flashing("error" -> Messages("login.backend.failed"))
            },            
            timeout => Redirect(routes.Application.index).flashing("error" -> Messages("login.backend.failed"))
          )
      }
    }
  }

  // Twitter connection settings
  val twitterClientId = Play.current.configuration.getString("twitter.clientId").getOrElse("")
  val twitterSecret = Play.current.configuration.getString("twitter.secret").getOrElse("")
  val twitterRequestTokenURL = Play.current.configuration.getString("twitter.requestTokenURL").getOrElse("")
  val twitterAuthorizeURL = Play.current.configuration.getString("twitter.authorizeURL").getOrElse("")
  val twitterAccessTokenURL = Play.current.configuration.getString("twitter.accessTokenURL").getOrElse("")
  val twitterCredentials = Play.current.configuration.getString("twitter.credentials").getOrElse("")

  val KEY = ConsumerKey(twitterClientId, twitterSecret)
  val TWITTER = OAuth(ServiceInfo(twitterRequestTokenURL, twitterAccessTokenURL, twitterAuthorizeURL, KEY), false)

  // GitHub connection settings
  val githubClientId = Play.current.configuration.getString("github.clientId").getOrElse("")
  val githubSecret = Play.current.configuration.getString("github.secret").getOrElse("")
  val githubAuthorizeURL = Play.current.configuration.getString("github.authorizeURL").getOrElse("")
  val githubGetTokenURL = Play.current.configuration.getString("github.getTokenURL").getOrElse("")
  val githubGetUser = Play.current.configuration.getString("github.getUser").getOrElse("")

  // google settings
  val googleOpenIdUrl = Play.current.configuration.getString("google.openid").getOrElse("")

  /**
   * Processes github Authentication
   */
  def githubAuth() = Action {
    implicit request =>
      Logger.info("Authentication.githubAuth called with request %s".format(request))
      //see if we got an answer from github
      val code = request.queryString.get("code") //code received
      val error = request.queryString.get("error") //error: user denied

      //if we got an error code, redirect to main, else proceed with authentication in github
      error.map {
        message =>
          Logger.warn("Authentication.githubAuth the request was reject with an error message by github: %s".format(message))
          Redirect(routes.Application.index).flashing("warning" -> Messages("login.backend.cancel"))
      }.getOrElse {
        // no error code, check for github code
        code.map {
          code =>
            Logger.info("Authentication.githubAuthRequest received auth code from github %s".format(code))

            // we got an auth code, this means we did the GET request and user granted permission to the application, now we do the POST request for the token
            val result: Promise[ws.Response] = {
              val postRequest = WS.url(githubGetTokenURL).withQueryString("client_id" -> githubClientId, "code" -> code(0), "client_secret" -> githubSecret)
              Logger.debug("Authentication.githubAuthRequest preparing post %s".format(postRequest))
              Logger.info("Authentication.githubAuthRequest preparing post to obtain token")
              postRequest.post("")
            }

            //do post async to avoid blocking the thread
            Async {
              result.orTimeout("TimeoutConnectGithub", 10000).map {
                retrieve =>
                  retrieve.fold(
                    response => processGithubResponse(response.body),
                    timeout => Redirect(routes.Application.index).flashing("error" -> Messages("login.backend.failed"))
                  )
              }
            }

        }.getOrElse {
          //neither code nor error was received, this means we didn't call from github, go to github authorization
          Logger.info("Authentication.githubAuthRequest redirecting to GitHub for authentication")
          Redirect(url = githubAuthorizeURL, queryString = Map("client_id" -> List(githubClientId)))
        }
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

        retrieveUserDataFromService(userData, GitHub)

      }
      case _ => {
        Logger.warn("Authentication.processGithubResponse couldn't find the access token for body %s".format(body))
        Redirect(routes.Application.index).flashing("error" -> Messages("login.backend.failed"))
      }
    }
  }


  /**
   * Processes twitter authentication
   */
  def twitterAuth() = Action {
    implicit request =>

    /**
     * Token pair for Twitter authentication
     */
      def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
        for {
          token <- request.session.get("token")
          secret <- request.session.get("secret")
        } yield {
          RequestToken(token, secret)
        }
      }


      Logger.info("Authentication.twitterAuth request received to authenticate with Twitter %s".format(request))
      request.queryString.get("oauth_verifier").flatMap(_.headOption).map {
        verifier =>
          val tokenPair = sessionTokenPair(request).get
          // We got the verifier; now get the access token and log user
          TWITTER.retrieveAccessToken(tokenPair, verifier) match {
            case Right(t) => {

                // Get user data
                val userData: Promise[ws.Response] = {
                  Logger.info("Authentication.twitterAuth retrieving user data")
                  WS.url(twitterCredentials).sign(OAuthCalculator(KEY, t)).get()
                }

                retrieveUserDataFromService(userData, Twitter)
            }
            case Left(e) => {
              Logger.error("Authentication.twitterAuth error while creating request token", e)
              Redirect(routes.Application.index).flashing("error" -> Messages("login.backend.failed"))
            }
          }

      }.getOrElse {
        Logger.info("Authentication.twitterAuth generating token for Twitter request")
        TWITTER.retrieveRequestToken(routes.Authentication.twitterAuth.absoluteURL()) match {
          case Right(t) => {
            Logger.info("Authentication.twitterAuth token generated, redirecting to Twitter for acceptance")
            // We received the unauthorized tokens in the OAuth object - store it before we proceed
            Redirect(TWITTER.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
          }
          case Left(e) => {
            Logger.error("Authentication.twitterAuth error while creating request token", e)
            Redirect(routes.Application.index).flashing("error" -> Messages("login.backend.failed"))
          }
        }
      }
  }

  /**
   * Authentication with Google
   * See  axchema to retrieve data  http://www.axschema.org/types/
   */
  def googleAuth() = Action { implicit request =>

    AsyncResult(OpenID.redirectURL(googleOpenIdUrl, routes.Authentication.openIDCallback.absoluteURL(),
      Seq("fullname" -> "http://axschema.org/namePerson",
          "firstname" -> "http://axschema.org/namePerson/first",
          "lastname" -> "http://axschema.org/namePerson/last",
          "country" -> "http://axschema.org/contact/country/home"
      )
    ).extend( _.value match {
          case Redeemed(url) => Redirect(url)
          case Thrown(t) => {
            Logger.error("Authentication.googleAuth error on redirect to open id", t)
            Redirect(routes.Application.index).flashing("error" -> Messages("login.backend.failed"))
          }
    }))
  }


  /**
   * OpenID callback
   */
  def openIDCallback = Action { implicit request =>
    AsyncResult(
      OpenID.verifiedId.extend( _.value match {
        case Redeemed(info) => {
          Google.authenticateUser( Google.processUser(info) )
        }
        case Thrown(t) => {
          Logger.error("Authentication.openIDCallback error on callback from open id", t)
          Redirect(routes.Application.index).flashing("error" -> Messages("login.backend.failed"))
        }
      })
    )
  }


  /**
   * Removes user session and redirects to home page
   */
  def logout() = Authenticated {
    implicit request =>
      Logger.info("Authentication.logout called for user %d".format(request.user.id.get))
      Redirect(routes.Application.index).flashing("info" -> Messages("login.backend.logout")).withNewSession
  }

}
