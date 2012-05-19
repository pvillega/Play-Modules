package models

import java.util.Date
import anorm.SqlParser._
import play.api.cache.Cache
import play.api.db.DB
import play.Logger
import anorm._
import play.api.Play.current


/**
 * Created with IntelliJ IDEA.
 * User: pvillega
 * Date: 14/05/12
 * Time: 21:20
 * Releases, linked to Modules
 */

/* We set some default parameters to avoid issues with Form */
case class Release(id: Pk[Long] = NotAssigned, name: String, description: Option[String] = None, moduleid: Long = -1, created: Date = new Date)

/**
 * Release object
 */
object Release {

  val releaseCacheKey = "Rs"

  // Parsers

  /**
   * Parse a Release from a ResultSet
   */
  val simple = {
      get[Pk[Long]]("release.id") ~
      get[String]("release.name") ~
      get[Option[String]]("release.description") ~
      get[Long]("release.plugin") ~
      get[Date]("release.created") map {
      case id ~ name ~ description ~ moduleid ~ created => Release(id, name, description, moduleid, created)
    }
  }

  // Queries

  /**
   * Retrieve a Release by the id.
   *
   * @param id the id of the release to retrieve
   */
  def findById(id: Long): Option[Release] = {
    Cache.getOrElse(releaseCacheKey + id, 60*60) {
      DB.withConnection {
        implicit connection =>
          SQL("select * from release where id = {id}").on('id -> id).as(Release.simple.singleOpt)
      }
    }
  }

  /**
   * Returns a list of the X most recent releases linked to the given module
   *
   * @param modid the id of the module that owns the releases
   * @param size size of the list, default is 20
   * @param from start position, used to skip initial x releases
   */
  def fetchRecentReleases(modid: Long, size : Int = 20, from: Int = 0) = {
    Logger.debug("Release.fetchRecentReleases with params: modid[%d] size[%d] from[%d]".format(modid, size, from))
      DB.withConnection {
        implicit connection =>

        SQL(
          """
              select *
              from release
              where plugin = {plugin}
              order by created desc
              limit {pageSize} offset {offset}
          """
        ).on(
          'pageSize -> size,
          'offset -> from,
          'plugin -> modid
        ).as(Release.simple *)
      }
  }

  /**
   * Returns a page of Release
   *
   * @param page Page to display
   * @param pageSize Number of elements per page
   * @param orderBy property used for sorting
   * @param modid the id of the module that owns the release
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, modid: Long) = {
    val offset = pageSize * page
    val mode = if (orderBy > 0) "ASC NULLS FIRST" else "DESC NULLS LAST"

    Logger.debug("Release.list with params: page[%d] pageSize[%d] orderBy[%d] module[%d] order[%s]".format(page, pageSize, orderBy, modid, mode))

    DB.withConnection {
      implicit connection =>

      // An explanation on the format applied to the SQL String: for some reason the JDBC driver for postgresql doesn't like it when you replace the
      // order by param in a statement. It gets ignored. So we have to provide it in the query before the driver processes the statement.
      // Now you'll be screaming "SQL INJECTION". Relax. Although it could happen, orderBy is an integer (which we turn into abs value for more safety).
      // If you try to call the controller that provides orderBy with a string, Play returns a 404 error. So only integers are allowed. And if there is no
      // column corresponding to the given integer, the order by is ignored. So, not ideal, but not so bad.
        val releases = SQL(
          """
            select *
            from release
            where plugin = {plugin}
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'plugin -> modid
        ).as(Release.simple *)

        val totalRows = SQL(
          """
            select count(*) from release
            where plugin = {plugin}
          """
        ).on(
          'plugin -> modid
        ).as(scalar[Long].single)

        Page(releases, page, offset, totalRows, pageSize)
    }

  }

  /**
   * Insert a new Release
   *
   * @param release the release values
   * @param modid id of the module that owns the release
   * @return the release created (including id)
   */
  def create(release: Release, modid: Long) = {
    DB.withConnection {
      implicit connection =>
      //we use many default values from the db
        val id = SQL(
          """
            insert into release (name, plugin, description) values ({name}, {plugin} , {description})
            returning id
          """
        ).on(
          'name -> release.name,
          'plugin -> modid,
          'description -> release.description
        ).as(long("id").single)

        //store object in cache for later retrieval
        val newRelease = release.copy(id = Id(id), moduleid = modid)
        Cache.set(releaseCacheKey + id, newRelease, 60*60)
        newRelease
    }
  }

  /**
   * Updates the release details in the database
   *
   * @param id the id of the release to update
   * @param release the details to update
   * @param modid the id of the module that owns the release
   * @param userid the id of the user that requests the removal
   */
  def update(id: Long, release: Release, modid: Long, userid: Long) = {
    DB.withConnection {
      implicit connection =>

        SQL(
          """
            update release
            set name = {name}, description = {description}
            where id = {id}  and plugin = {plugin}
            and plugin in (select id from plugin where author = {author} and id = {plugin})
          """
        ).on(
          'id -> id,
          'name -> release.name,
          'description -> release.description,
          'plugin -> modid,
          'author -> userid
        ).executeUpdate()

        //store object in cache for later retrieval.
        val cached = findById(id).get
        Cache.set(releaseCacheKey + id, cached.copy(name = release.name, description = release.description), 60*60)
    }
  }

  /**
   * Deletes the release with the given id
   *
   * @param id the id of the release to delete
   * @param modid the id of the module that owns the release
   * @param userid the id of the user that requests the removal
   */
  def delete(id: Long, modid: Long, userid: Long) = {
    DB.withConnection {
      implicit connection =>
        val cached = findById(id).get
        SQL(
          """
            delete from release
            where id = {id} and plugin = {plugin}
            and plugin in (select id from plugin where author = {author} and id = {plugin})
          """
        ).on(
          'id -> id,
          'plugin -> modid,
          'author -> userid
        ).executeUpdate()

        //removes from cache
        Cache.set(releaseCacheKey + id , None, 60)
    }
  }

}
