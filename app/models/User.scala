package models

import java.util.Date
import anorm.SqlParser._
import play.api.db.DB
import anorm._
import play.api.Play.current
import play.api.cache.Cache
import play.Logger


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
    Cache.getOrElse(userCacheKey + id, 60*60) {
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
      val id =  SQL(
          """
            insert into publisher (name, avatar, githubId, twitterId, googleId, url, bio, location)
            values ({name}, {avatar}, {githubId}, {twitterId}, {googleId}, {url}, {bio}, {location})
            returning id
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
        ).as(int("id").single)

        //store object in cache for later retrieval
        Cache.set(userCacheKey + id, user.copy(id = Id(id)), 60*60)
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
        Cache.set(userCacheKey + id, cached.copy(name = user.name, avatar = user.avatar, bio = user.bio, url = user.url, location = user.location), 60*60)
    }
  }

  /**
   * Updates user details from an administration screen
   * @param id the id of the user to update
   * @param user the details of the user
   */
  def updateUserAdministration(id: Long, user: User) = {
    DB.withConnection {
      implicit connection =>

        SQL(
          """
            update publisher
            set name = {name}, githubId = {githubId}, twitterId = {twitterId}, googleId = {googleId}, disabled = {disabled},
                admin = {admin}, avatar = {avatar}, url = {url}, bio = {bio}, location = {location}
            where id = {id}
          """
        ).on(
          'id -> id,
          'name -> user.name,
          'githubId -> user.githubId,
          'twitterId -> user.twitterId,
          'googleId -> user.googleId,
          'disabled -> user.disabled,
          'admin -> user.admin,
          'avatar -> user.avatar.map {
            avatar => avatar
          }.getOrElse(defaultAvatar),
          'bio -> user.bio,
          'url -> user.url,
          'location -> user.location
        ).executeUpdate()

        //store object in cache for later retrieval. This user should be in cache already so this should be quick
        val cached = findById(id).get
        Cache.set(userCacheKey + id, cached.copy(name = user.name, githubId = user.githubId, twitterId = user.twitterId, googleId = user.googleId, disabled = user.disabled, admin = user.admin, avatar = user.avatar, bio = user.bio, url = user.url, location = user.location), 60*60)
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

        Cache.set(userCacheKey + user.id, user.copy(disabled = true), 60*60)
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

        Cache.set(userCacheKey + user.id, user.copy(disabled = false), 60*60)
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
        Cache.set(userCacheKey + id, None, 60*60)
    }
  }

  /**
   * Returns a page of Users
   *
   * @param page Page to display
   * @param pageSize Number of elements per page
   * @param orderBy property used for sorting
   * @param filter Filter applied on the elements
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Page[User] = {
    val offset = pageSize * page
    val mode = if (orderBy > 0) "ASC NULLS FIRST" else "DESC NULLS LAST"

    Logger.debug("Users.list with params: page[%d] pageSize[%d] orderBy[%d] filter[%s] order[%s]".format(page, pageSize, orderBy, filter, mode))

    DB.withConnection {
      implicit connection =>

        // An explanation on the format applied to the SQL String: for some reason the JDBC driver for postgresql doesn't like it when you replace the
        // order by param in a statement. It gets ignored. So we have to provide it in the query before the driver processes the statement.
        // Now you'll be screaming "SQL INJECTION". Relax. Although it could happen, orderBy is an integer (which we turn into abs value for more safety).
        // If you try to call the controller that provides orderBy with a string, Play returns a 404 error. So only integers are allowed. And if there is no
        // column corresponding to the given integer, the order by is ignored. So, not ideal, but not so bad.
        val users = SQL(
          """
            select * from publisher
            where name ilike {filter}
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'filter -> filter
        ).as(User.simple *)

        val totalRows = SQL(
          """
            select count(*) from publisher
            where name like {filter}
          """
        ).on(
          'filter -> filter
        ).as(scalar[Long].single)

        Page(users, page, offset, totalRows)
    }

  }

}
