package models

import java.util.Date
import anorm.SqlParser._
import play.api.db.DB
import anorm._
import play.api.Play.current
import play.api.cache.Cache

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 03/03/12
 * Time: 11:48
 * Model for users in the application
 */

case class User(id: Pk[Long] = NotAssigned, name: String, githubId: Option[Long] = None, disabled: Boolean = false, admin: Boolean = false, created: Date = new Date, lastAccess: Date = new Date, avatar: Option[String] = Some(User.defaultAvatar), location: Option[String] = None, blog: Option[String] = None, githubUrl: Option[String] = None, bio: Option[String] = None)


object User {

  val defaultAvatar = "http://www.gravatar.com/avatar/00000000000000000000000000000000"
  val userCacheKey = "User"
  val userCacheKeygithub = userCacheKey + "gh"
  // Parsers

  /**
   * Parse a User from a ResultSet
   */
  val simple = {
    get[Pk[Long]]("user.id") ~
      get[String]("user.name") ~
      get[Option[Long]]("user.githubId") ~
      get[Boolean]("user.disabled") ~
      get[Boolean]("user.admin") ~
      get[Date]("user.created") ~
      get[Date]("user.lastAccess") ~
      get[Option[String]]("user.avatar") ~
      get[Option[String]]("user.location") ~
      get[Option[String]]("user.blog") ~
      get[Option[String]]("user.githubUrl") ~
      get[Option[String]]("user.bio") map {
      case id ~ name ~ githubId ~ disabled ~ admin ~ created ~ lastAccess ~ avatar ~ location ~ blog ~ githubUrl ~ bio => User(id, name, githubId, disabled, admin, created, lastAccess, avatar, location, blog, githubUrl, bio)
    }
  }

  // Queries

  /**
   * Retrieve a User from the id.
   * 
   * @param id the id of the user to retrieve
   */
  def findById(id: Long): Option[User] = {
    Cache.getOrElse(userCacheKey + id) {
      DB.withConnection {
        implicit connection =>
          SQL("select * from \"user\" where id = {id}").on('id -> id).as(User.simple.singleOpt)
      }
    }
  }


  /**
   * Retrieve a User by its github user id.
   * 
   * @param githubId the id from github service of the user we want to find
   */
  def findByGithubId(githubId: Long): Option[User] = {
    Cache.getOrElse(userCacheKeygithub + githubId) {
      DB.withConnection {
        implicit connection =>
          SQL("select * from \"user\" where githubId = {githubId}").on('githubId -> githubId).as(User.simple.singleOpt)
      }
    }
  }

  /**
   * Updates the user record setting the last access date 
   * 
   * @param id the id of the user that just logged in
   */
  def updateLastAccess(id: Long) = {
    DB.withConnection {
      implicit connection =>
        SQL(
          """
            update "user"
            set lastAccess = {lastAccess}
            where id = {id}
          """
        ).on(
          'id -> id,
          'lastAccess -> new Date
        ).executeUpdate()
    }
  }


  /**
   * Insert a new User.
   *
   * @param user the user values.
   * @return the db id assigned to the new user
   */
  def create(user: User) = {
    DB.withConnection {
      implicit connection =>
      //we use many default values from the db
        SQL(
          """
            insert into "user" (name, avatar, githubId, location, blog, githubUrl, bio)
            values ({name}, {avatar}, {githubId}, {location}, {blog}, {githubUrl}, {bio})
          """
        ).on(
          'name -> user.name,
          'avatar -> user.avatar,
          'githubId -> user.githubId,
          'location -> user.location,
          'blog -> user.blog,
          'githubUrl -> user.githubUrl,
          'bio -> user.bio
        ).executeUpdate()

        // as per http://wiki.postgresql.org/wiki/FAQ this should not bring race issues
        val id = SQL("select currval(pg_get_serial_sequence('user', 'id'))").as(scalar[Long].single)

        //store object in cache for later retrieval
        Cache.set(userCacheKey + id, user.copy(id = Id(id)))
        id
    }
  }

  /**
   * Disables a user.
   *
   * @param user the user to disable.
   */
  def disable(user: User) = {
    DB.withConnection {
      implicit connection =>
        SQL(
          """
            update "user"
            set disabled = {disabled}
            where id = {id}
          """
        ).on(
          'id -> user.id,
          'disabled -> true
        ).executeUpdate()

        Cache.set(userCacheKey + user.id, user.copy(disabled = true))
    }
  }

  /**
   * Enables a user.
   *
   * @param user the user to enable.
   */
  def enable(user: User) = {
    DB.withConnection {
      implicit connection =>
        SQL(
          """
            update 'user'
            set disabled = {disabled}
            where id = {id}
          """
        ).on(
          'id -> user.id,
          'disabled -> false
        ).executeUpdate()

        Cache.set(userCacheKey + user.id, user.copy(disabled = false))
    }
  }

}