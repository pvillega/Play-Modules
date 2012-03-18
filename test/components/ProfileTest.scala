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
import anorm.Id
import controllers.Profile
import play.api.mvc.{Session, AnyContentAsEmpty}
import play.api.test.Helpers._

class ProfileTest extends Specification {

  //we use this trait in some tests to create a user for the test and remove it afterwards, as we are using postgreSQL and
  //we don't want to dirty the db
  trait dbUser extends BeforeAfter {
    val user: User = User(name = "testNewUser",
      avatar = Some("none"),
      githubId = Some(101),
      twitterId = Some(100),
      googleId = Some("googleOid"),
      bio = Some("new bio"),
      url = Some("new url"),
      location = Some("new location"))

    //set val  when requested
    val id: Long = running(FakeApplication()) {
      User.create(user)
    }

    def after = {
      running(FakeApplication()) {
        //clean db
        User.delete(id)
      }
    }

    def before = {
      //nothing
    }
  }


  //TODO: some tests fail, due to session values. See http://stackoverflow.com/questions/9753654/add-values-to-session-during-testing-fakerequest-fakeapplication

  "Profile controller" should {

    "respond to the index Action" in new dbUser {
      running(FakeApplication()) {
        val result = controllers.Profile.index(id)(FakeRequest())

        status(result) must equalTo(OK)
        contentType(result) must beSome("text/html")
        charset(result) must beSome("utf-8")
        contentAsString(result) must contain(Messages("profile.title", "testNewUser"))
        contentAsString(result) must contain(Messages("profile.name"))
      }
    }

    "return 404 when user doens't exist" in {
      running(FakeApplication()) {
        val result = controllers.Profile.index(-1)(FakeRequest())

        status(result) must equalTo(NOT_FOUND)
      }
    }

    "respond to the edit Action" in {
      running(FakeApplication()) {

        val notLoggedIn = controllers.Profile.edit()(FakeRequest())
        status(notLoggedIn) must equalTo(NOT_FOUND)

        // create a user
        val result = controllers.Profile.edit()(FakeRequest())

        status(result) must equalTo(OK)
        contentType(result) must beSome("text/html")
        charset(result) must beSome("utf-8")
        contentAsString(result) must contain(Messages("profile.edit"))
      }
    }

    "edit my user" in new dbUser {
      running(FakeApplication()) {

        val notLoggedIn = controllers.Profile.save()(FakeRequest())
        status(notLoggedIn) must equalTo(NOT_FOUND)

        val badResult = controllers.Profile.save()(FakeRequest())
        status(badResult) must equalTo(BAD_REQUEST)

        val result = controllers.Profile.save()(
          FakeRequest().withFormUrlEncodedBody("name" -> "testSave", "bio" -> "my new bio", "location" -> "a new location", "url" -> "www.test.es", "avatar" -> "avatar modified")
        )

        status(result) must equalTo(SEE_OTHER)
        redirectLocation(result) must beSome.which(_ == "/profile")
        flash(result).get("success") must beSome.which(_ == Messages("profile.updated"))
      }
    }

    "return 404 when trying to edit a user when not logged in" in new dbUser {
      running(FakeApplication()) {
        val result = controllers.Profile.edit()(FakeRequest())

        status(result) must equalTo(NOT_FOUND)
      }
    }

  }


  "Profile templates" should {

    "render index template when visiting another user" in {
      val user = User(name = "test", admin = true)
      val html = views.html.profile.index(user, false)(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("app.title"))
      contentAsString(html) must contain(Messages("profile.title"))
      contentAsString(html) must contain(Messages("profile.name", user.name))
      contentAsString(html) must not contain (Messages("profile.admin"))
    }

    "show admin link when visited by logged in admin" in {
      val user = User(id = Id(99), name = "test", admin = true)
      val html = views.html.profile.index(user, true)(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("profile.title"))
      contentAsString(html) must contain(Messages("profile.name", user.name))
      contentAsString(html) must contain(Messages("profile.admin"))
    }

    "show edit form when requested" in {
      val user = User(id = Id(99), name = "testName", bio = Some("my bio"), location = Some("my location"), avatar = Some("my avatar"), url = Some("my nice url"))
      val form = Profile.profileForm.fill(user)
      val html = views.html.profile.edit(form, user.id.get)(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("profile.edit"))
      contentAsString(html) must contain(user.name)
      contentAsString(html) must contain(user.bio.get)
      contentAsString(html) must contain(user.location.get)
      contentAsString(html) must contain(user.avatar.get)
      contentAsString(html) must contain(user.url.get)
      contentAsString(html) must contain(Messages("profile.save"))
      contentAsString(html) must contain(Messages("profile.cancel"))
    }

    "edit form shows error messages if form fails validation" in {
      val user = User(id = Id(99), name = "", bio = Some("my bio"), location = Some("my location"), avatar = Some("my avatar"), url = Some("my nice url"))
      val form = Profile.profileForm.fillAndValidate(user)
      val html = views.html.profile.edit(form, user.id.get)(FakeRequest())

      contentType(html) must equalTo("text/html")
      contentAsString(html) must contain(Messages("profile.edit"))
      contentAsString(html) must contain(user.name)
      contentAsString(html) must contain(Messages("error.required"))
      contentAsString(html) must contain(user.bio.get)
      contentAsString(html) must contain(user.location.get)
      contentAsString(html) must contain(user.avatar.get)
      contentAsString(html) must contain(user.url.get)
      contentAsString(html) must contain(Messages("profile.save"))
      contentAsString(html) must contain(Messages("profile.cancel"))
    }

  }

}

