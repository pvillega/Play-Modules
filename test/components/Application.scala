package components

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 10/03/12
 * Time: 17:14
 * Test of controller Application and its templates
 */

import org.specs2.mutable._

import play.api.test._
import play.api.i18n.Messages
import play.api.test.Helpers._

class Application extends Specification {

  "Application controller" should {

    "respond to the index Action" in {
      val Some(result) = routeAndCall(FakeRequest(GET, "/"))

      status(result) must equalTo(OK)
      contentType(result) must beSome("text/html")
      charset(result) must beSome("utf-8")
      contentAsString(result) must contain(Messages("app.title"))
      contentAsString(result) must contain(Messages("landing.detailsTitle"))
    }
  }

  "Application templates" should {

    "render index template" in {
      val html = views.html.index()(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("app.title"))
      contentAsString(html) must contain(Messages("landing.detailsTitle"))
    }

    "render 404 template" in {
      val html = views.html.errors.error404("path")(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("notFound.header"))
    }

    "render error template" in {
      val html = views.html.errors.error(new Exception("test"))(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("error.header"))
    }


  }

}

