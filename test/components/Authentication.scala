package components

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 10/03/12
 * Time: 17:14
 * Test of controller Profile and its templates
 */

import org.specs2.mutable._

import play.api.test._
import play.api.i18n.Messages
import play.api.test.Helpers._

class Authentication extends Specification {

  "Authentication controller" should {

    "respond to the index Action" in {
      running(TestServer(3333)) {
        val Some(result) = routeAndCall(FakeRequest(GET, "/authentication"))

        status(result) must equalTo(OK)
        contentType(result) must beSome("text/html")
        charset(result) must beSome("utf-8")
        contentAsString(result) must contain(Messages("login.title"))
      }
    }

    "respond to log wiht GitHub action" in {
      running(TestServer(3333)) {
        val Some(result) = routeAndCall(FakeRequest(GET, "/githubAuth"))

        status(result) must equalTo(SEE_OTHER)
        //this will redirect to github, no more testing in here, do it on integration
      }
    }

    "redirect to Home on logout" in {
      running(TestServer(3333)) {
        val Some(result) = routeAndCall(FakeRequest(GET, "/logout"))

        status(result) must equalTo(SEE_OTHER)
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
    }
  }

}

