package integration

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 10/03/12
 * Time: 17:14
 * Integration test of the application, only one per app recommended due to time it takes to run
 */

package test

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.Messages
import play.api.Play

class Integration extends Specification {

  "Application" should {

    "work from within a browser" in {
      //due to the config of remote services like github, we have to use port 9000
      running(TestServer(9000), HTMLUNIT) {
        browser =>
        // get details to login into github from config
          val gitUsername = Play.current.configuration.getString("github.username").getOrElse("")
          val gitPassword = Play.current.configuration.getString("github.password").getOrElse("")


        // load main page
          browser.goTo("http://localhost:9000/")
          browser.$("div.container a.brand").first.getText must equalTo(Messages("app.title"))
          browser.$("header div.inner h1").first.getText must equalTo(Messages("app.title"))

          // go to 404 page
          browser.goTo("http://localhost:9000/abcdefghijkl")
          browser.$("div.container a.brand").first.getText must equalTo(Messages("app.title"))
          browser.pageSource must contain(Messages("notFound.header"))

          // validate some static resources
          browser.goTo("http://localhost:9000/humans.txt")
          browser.pageSource must contain("humanstxt.org")

          browser.goTo("http://localhost:9000/robots.txt")
          browser.pageSource must contain("www.robotstxt.org")

          browser.goTo("http://localhost:9000/crossdomain.xml")
          browser.pageSource must contain("cross-domain-policy")

          browser.goTo("http://localhost:9000/favicon.ico")

          // test logout without being logged in, should redirect to main page with logout message
          browser.goTo("http://localhost:9000/logout")
          browser.$(".container .brand").first.getText must equalTo(Messages("app.title"))
          browser.$("header div h1").first.getText must equalTo(Messages("app.title"))
          browser.pageSource must contain(Messages("login.backend.logout"))

          //go to authentication page
          browser.goTo("http://localhost:9000/authentication")
          browser.$("header h1").first.getText must equalTo(Messages("login.title"))
          browser.pageSource must contain(Messages("login.text"))
          browser.$("a.github").first.isDisplayed must beTrue

          //login into github but deny authorization
          browser.goTo("http://localhost:9000/githubAuth")
          browser.$("input[name=login]").text(gitUsername)
          browser.$("input[name=password]").text(gitPassword)
          browser.$("input[name=commit]").click()
          //deny access
          browser.$("button[name=cancel]").click()

          //login into github and authorize
          browser.goTo("http://localhost:9000/githubAuth")
          browser.$("button[name=authorize]").click()
          //after authorize we are in profile page
          val profileUrl = browser.url()
          browser.pageSource must contain(Messages("profile.title", ""))
          browser.$("div.page-header h2").first.getText must equalTo(Messages("profile.modules"))
          browser.$("div.page-header h2").get(1).getText must equalTo(Messages("profile.demos"))

          //go to profile
          browser.goTo(profileUrl)
          browser.$("div.container a.brand").first.getText must equalTo(Messages("app.title"))
          browser.pageSource must contain(Messages("profile.title", ""))
          browser.$("div.page-header h2").first.getText must equalTo(Messages("profile.modules"))
          browser.$("div.page-header h2").get(1).getText must equalTo(Messages("profile.demos"))

          //logout and remove session
          browser.goTo("http://localhost:9000/logout")
          browser.$(".container .brand").first.getText must equalTo(Messages("app.title"))
          browser.$("header div h1").first.getText must equalTo(Messages("app.title"))
          browser.pageSource must contain(Messages("login.backend.logout"))

          //go to profile while not logged in
          browser.goTo(profileUrl)
          browser.$("div.container a.brand").first.getText must equalTo(Messages("app.title"))
          browser.pageSource must contain(Messages("profile.title", ""))
          browser.$("div.page-header h2").first.getText must equalTo(Messages("profile.modules"))
          browser.$("div.page-header h2").get(1).getText must equalTo(Messages("profile.demos"))
      }
    }

  } // Application

}

