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

case class User(id: Pk[Long] = NotAssigned, name: String, githubId: Option[Long] = None, twitterId: Option[Long] = None, googleId: Option[String] = None, disabled: Boolean = false, admin: Boolean = false, created: Date = new Date, lastAccess: Date = new Date, avatar: Option[String] = Some(User.defaultAvatar), url: Option[String] = None, bio: Option[String] = None, location: Option[String] = None)


object User {

  val defaultAvatar = "http://www.gravatar.com/avatar/00000000000000000000000000000000?s=160"
  val userCacheKey = "Usr"

  // Parsers

  /**
   * Parse a User from a ResultSet
   */
  val simple = {
    get[Pk[Long]]("publisher.id") ~
      get[String]("publisher.name") ~
      get[Option[Long]]("publisher.githubId") ~
      get[Option[Long]]("publisher.twitterId") ~
      get[Option[String]]("publisher.googleId") ~
      get[Boolean]("publisher.disabled") ~
      get[Boolean]("publisher.admin") ~
      get[Date]("publisher.created") ~
      get[Date]("publisher.lastAccess") ~
      get[Option[String]]("publisher.avatar") ~
      get[Option[String]]("publisher.url") ~
      get[Option[String]]("publisher.location") ~
      get[Option[String]]("publisher.bio") map {
      case id ~ name ~ githubId ~ twitterId ~ googleId ~ disabled ~ admin ~ created ~ lastAccess ~ avatar ~ url ~ location ~ bio => User(id, name, githubId, twitterId, googleId, disabled, admin, created, lastAccess, avatar, url, bio, location)
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
          SQL("select * from publisher where id = {id}").on('id -> id).as(User.simple.singleOpt)
      }
    }
  }


  /**
   * Retrieve a User by its github user id.
   *
   * @param githubId the id from github service of the user we want to find
   */
  def findByGithubId(githubId: Long): Option[User] = {
    DB.withConnection {
      implicit connection =>
        SQL("select * from publisher where githubId = {githubId}").on('githubId -> githubId).as(User.simple.singleOpt)
    }
  }

  /**
   * Retrieves a user by its google id
   * @param id google id of the user
   */
  def findByGoogleId(id: String) = {
    DB.withConnection {
      implicit connection =>
        SQL("select * from publisher where googleId like {googleId} ").on('googleId -> id).as(User.simple.singleOpt)
    }
  }

  /**
   * Retrieve a User by its twitterId user id.
   *
   * @param twitterId the id from twitter service of the user we want to find
   */
  def findByTwitterId(twitterId: Long): Option[User] = {
    DB.withConnection {
      implicit connection =>
        SQL("select * from publisher where twitterId = {twitterId}").on('twitterId -> twitterId).as(User.simple.singleOpt)
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
        val date = new Date
        SQL(
          """
            update publisher
            set lastAccess = {lastAccess}
            where id = {id}
          """
        ).on(
          'id -> id,
          'lastAccess -> date
        ).executeUpdate()

        date
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
            insert into publisher (name, avatar, githubId, twitterId, googleId, url, bio, location)
            values ({name}, {avatar}, {githubId}, {twitterId}, {googleId}, {url}, {bio}, {location})
          """
        ).on(
          'name -> user.name,
          'avatar -> user.avatar,
          'githubId -> user.githubId,
          'twitterId -> user.twitterId,
          'googleId -> user.googleId,
          'url -> user.url,
          'bio -> user.bio,
          'location -> user.location
        ).executeUpdate()

        // as per http://wiki.postgresql.org/wiki/FAQ this should not bring race issues
        val id = SQL("select currval(pg_get_serial_sequence('publisher', 'id'))").as(scalar[Long].single)

        //store object in cache for later retrieval
        Cache.set(userCacheKey + id, user.copy(id = Id(id)))
        id
    }
  }

  /**
   * Updates the user details in the database
   *
   * @param id the id of the user to update
   * @param user the details to update
   */
  def updateUser(id: Long, user: User) = {
    DB.withConnection {
      implicit connection =>

        SQL(
          """
            update publisher
            set name = {name}, avatar = {avatar}, url = {url}, bio = {bio}, location = {location}
            where id = {id}
          """
        ).on(
          'id -> id,
          'name -> user.name,
          'avatar -> user.avatar.map {
            avatar => avatar
          }.getOrElse(defaultAvatar),
          'bio -> user.bio,
          'url -> user.url,
          'location -> user.location
        ).executeUpdate()

        //store object in cache for later retrieval. This user should be in cache already so this should be quick
        val cached = findById(id).get
        Cache.set(userCacheKey + id, cached.copy(name = user.name, avatar = user.avatar, bio = user.bio, url = user.url, location = user.location))
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
            update publisher
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
            update publisher
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

  /**
   * Removes a user from the db
   *
   * @param id the id of the user to remove
   */
  def delete(id: Long) = {
    DB.withConnection {
      implicit connection =>
        SQL(
          """
            delete from publisher
            where id = {id}
          """
        ).on(
          'id -> id
        ).executeUpdate()

        // empty cache
        Cache.set(userCacheKey + id, None, 1)
    }
  }

}