package model

import org.specs2.mutable.Specification
import org.specs2.mutable.BeforeAfter
import play.api.test.Helpers._
import play.api.test.FakeApplication
import models.User
import java.util.Date

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 17/03/12
 * Time: 13:35
 * Tests user model.
 * In the app we use PostgreSQL-related SQL so we can't use an in-memory db, which means that we will need to use a
 * BeforeAfter trait to keep the db clean from test data
 */
class UserTest extends Specification {

  // -- Date helpers
  def formatDate(date: Date) = new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(date)

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


  "User model" should {

    "be retrieved by id" in new dbUser {
      running(FakeApplication()) {

        val Some(user) = User.findById(id)

        user.name must equalTo("testNewUser")
        user.avatar.get must equalTo("none")
        user.admin must beFalse
        user.disabled must beFalse
      }
    }

    "be retrieved by github id" in new dbUser {
      running(FakeApplication()) {

        val Some(user) = User.findByGithubId(101)

        user.name must equalTo("testNewUser")
        user.avatar.get must equalTo("none")
        user.admin must beFalse
        user.disabled must beFalse
      }

    }

    "be retrieved by twitter id" in new dbUser {
      running(FakeApplication()) {

        val Some(user) = User.findByTwitterId(100)

        user.name must equalTo("testNewUser")
        user.avatar.get must equalTo("none")
        user.admin must beFalse
        user.disabled must beFalse
      }

    }

    "be retrieved by google id" in new dbUser {
      running(FakeApplication()) {

        val Some(user) = User.findByGoogleId("googleOid")

        user.name must equalTo("testNewUser")
        user.avatar.get must equalTo("none")
        user.admin must beFalse
        user.disabled must beFalse
      }

    }

    "be enabled" in new dbUser {
      running(FakeApplication()) {

        val Some(user) = User.findById(id)
        User.enable(user)

        val Some(enabled) = User.findById(id)
        enabled.disabled must beFalse
      }
    }

    "be disabled" in new dbUser {
      running(FakeApplication()) {

        val Some(user) = User.findById(id)
        User.disable(user)

        val Some(disabled) = User.findById(id)
        disabled.disabled must beTrue
      }
    }

    "be created" in {
      running(FakeApplication()) {

        val user: User = User(name = "testNewUser",
          avatar = Some("none"),
          githubId = None,
          twitterId = None,
          googleId = None,
          bio = Some("new bio"),
          url = Some("new url"),
          location = Some("new location"))
        val id = User.create(user)

        val Some(updated) = User.findById(id)
        updated.id.get mustEqual id
        updated.name mustEqual user.name
        updated.avatar mustEqual user.avatar
        updated.twitterId mustEqual user.twitterId
        updated.githubId mustEqual user.githubId
        updated.googleId mustEqual user.googleId
        updated.bio mustEqual user.bio
        updated.url mustEqual user.url
        updated.location mustEqual user.location

        User.delete(id)
      }

    }

    "be updated" in new dbUser {
      running(FakeApplication()) {

        val changes: User = User(name = "test2",
          avatar = Some("none"),
          bio = Some("new bio"),
          url = Some("new url"),
          location = Some("new location"))
        User.updateUser(id, changes)

        val Some(updated) = User.findById(id)
        updated.name mustEqual changes.name
        updated.avatar mustEqual changes.avatar
        updated.bio mustEqual changes.bio
        updated.url mustEqual changes.url
        updated.location mustEqual changes.location
        formatDate(updated.created) mustEqual formatDate(changes.created)

        User.delete(id)
      }

    }

    "be deleted" in {
      running(FakeApplication()) {

        val user: User = User(name = "testNewUser",
          avatar = Some("none"),
          githubId = None,
          twitterId = None,
          googleId = None,
          bio = Some("new bio"),
          url = Some("new url"),
          location = Some("new location"))
        val id = User.create(user)
        User.delete(id)

        val notThere = User.findById(id)
        notThere mustEqual None
      }

    }

    "have last access date updated" in new dbUser {
      running(FakeApplication()) {

        val date = User.updateLastAccess(id)

        val Some(updated) = User.findById(id)
        formatDate(updated.lastAccess) mustEqual (formatDate(date))
      }
    }

  } // User model

}