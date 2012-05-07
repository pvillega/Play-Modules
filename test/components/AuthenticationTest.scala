package components

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 10/03/12
 * Time: 17:14
 * Test of controller Authentication and its templates
 */

import org.specs2.mutable._

import play.api.test._
import play.api.i18n.Messages
import play.api.test.Helpers._

//TODO: all testing needs to be redone in 2.1 as we need FakeRequest to be able to get session values
class AuthenticationTest extends Specification {

  "Authentication controller" should {

    "respond to the index Action" in {
      running(FakeApplication()) {
        val result = controllers.Authentication.index()(FakeRequest())

        status(result) must equalTo(OK)
        contentType(result) must beSome("text/html")
        charset(result) must beSome("utf-8")
        contentAsString(result) must contain(Messages("login.title"))
      }
    }

    "respond to log with GitHub action" in {
      running(FakeApplication()) {
        val result = controllers.Authentication.githubAuth()(FakeRequest())

        status(result) must equalTo(SEE_OTHER)
        //TODO: we can't really test the full auth process in here, just the start
      }
    }

    "respond to log with Twitter action" in {
      running(FakeApplication()) {
        val result = controllers.Authentication.twitterAuth()(FakeRequest())

        status(result) must equalTo(SEE_OTHER)
        //TODO: we can't really test the full auth process in here, just the start
      }
    }

    "respond to log with Google action" in {
      running(FakeApplication()) {
        val result = controllers.Authentication.googleAuth()(FakeRequest())

        //TODO: openID redirect is an async call, we can't check status. How to validate?
      }
    }

    "redirect to Home on logout" in {
      running(FakeApplication()) {
        val result = controllers.Authentication.logout()(FakeRequest())

        status(result) must equalTo(SEE_OTHER)
        redirectLocation(result) must beSome.which(_ == "/")
        flash(result).get("info") must beSome.which(_ == Messages("login.backend.logout"))
      }
    }
  }

  "Authentication templates" should {

    "render index template" in {
      val html = views.html.authentication.index()(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("login.title"))
      // contains auth links?
      contentAsString(html) must contain("class=\"github\"")
      contentAsString(html) must contain("class=\"twitter\"")
      contentAsString(html) must contain("class=\"google\"")
    }
  }

}

