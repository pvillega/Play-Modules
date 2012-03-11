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
import models.User
import play.api.test.Helpers._
import anorm.Id
import play.api.cache.Cache

class Profile extends Specification {

  "Profile controller" should {

    "respond to the index Action" in {
      running(TestServer(3333)) {
        // create a user
        val user :User = User(name= "test" , admin = true)
        val id = User.create(user)

        val Some(result) = routeAndCall(FakeRequest(GET, "/profile/%d".format(id)))

        status(result) must equalTo(OK)
        contentType(result) must beSome("text/html")
        charset(result) must beSome("utf-8")
        contentAsString(result) must contain(Messages("profile.title", user.name))
        contentAsString(result) must contain(Messages("profile.name"))
      }
    }

    "return 404 when user doens't exist" in {
      running(TestServer(3333)) {
        val Some(result) = routeAndCall(FakeRequest(GET, "/profile/9999999"))

        status(result) must equalTo(NOT_FOUND)
      }
    }

  }


  "Profile templates" should {

    "render index template when visiting another user" in {
      val user = User(name= "test" , admin = true)
      val html = views.html.profile.index(user,false)(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("app.title"))
      contentAsString(html) must contain(Messages("profile.title"))
      contentAsString(html) must contain(Messages("profile.name", user.name))
      contentAsString(html) must not contain(Messages("profile.admin"))
    }

    "show admin link when visited by logged in admin" in {
      val user = User(name= "test" , admin = true)
      val html = views.html.profile.index(user,true)(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("profile.title"))
      contentAsString(html) must contain(Messages("profile.name", user.name))
      contentAsString(html) must contain(Messages("profile.admin"))
    }

  }

}

