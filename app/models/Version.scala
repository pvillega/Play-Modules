package models


import anorm.SqlParser._
import play.api.cache.Cache
import play.api.db.DB
import anorm._
import play.api.Play.current
import play.Logger

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 22/03/12
 * Time: 19:46
 * Model of Version entity
 */

case class Version(id: Pk[Long] = NotAssigned, name: String, parent: Option[Long] = None)

/**
 * Version object
 * Note that no delete method is provided as it could be dangerous (deleting referenced versions!)
 * If any error can't be fixed by editing the version, you'll need to go to the db and run sql manually
 */
object Version {

  val versionCacheKey = "Vsn"
  val allVersionsCacheKey = versionCacheKey + "All"

  // Parsers

  /**
   * Parse a Version from a ResultSet
   */
  val simple = {
    get[Pk[Long]]("version.id") ~
      get[String]("version.name") ~
      get[Option[Long]]("version.parent") map {
      case id ~ name ~ parent => Version(id, name, parent)
    }
  }
  
  val withParent = Version.simple ~ (str("pname") ?) map {
                      case version~name => (version, name)
                    }

  // Queries

  /**
   * Retrieve a Version from the id.
   *
   * @param id the id of the version to retrieve
   */
  def findById(id: Long): Option[Version] = {
    Cache.getOrElse(versionCacheKey + id, 60*60) {
      DB.withConnection {
        implicit connection =>
          SQL("select * from version where id = {id}").on('id -> id).as(Version.simple.singleOpt)
      }
    }
  }

  /**
   * Retrieve all versions for a select in a template
   */
  def allSelect(): Map[String, String] = {
    Cache.getOrElse(allVersionsCacheKey, 60*60) {
      DB.withConnection {
        implicit connection =>
          SQL("select id, name from version").as(long("id") ~ str("name") map(flatten) *).groupBy(_._1).map { case (k,v) => (k.toString,v.foldLeft("")((s,t) => s + t._2))}
      }
    }
  }


  /**
   * Insert a new Version.
   *
   * @param version the version values
   * @return the version created (including id)
   */
  def create(version: Version) = {
    DB.withConnection {
      implicit connection =>
      //we use many default values from the db
        SQL(
          """
            insert into version (name, parent)
            values ({name}, {parent})
          """
        ).on(
          'name -> version.name,
          'parent -> version.parent
        ).executeUpdate()

        // as per http://wiki.postgresql.org/wiki/FAQ this should not bring race issues
        val id = SQL("select currval(pg_get_serial_sequence('version', 'id'))").as(scalar[Long].single)

        //remove all version cache
        Cache.set(allVersionsCacheKey, None, 1)

        //store object in cache for later retrieval
        val newVersion = version.copy(id = Id(id))
        Cache.set(versionCacheKey + id, newVersion, 60)
        newVersion
    }
  }

  /**
   * Updates the version details in the database
   *
   * @param id the id of the version to update
   * @param version the details to update
   */
  def update(id: Long, version: Version) = {
    DB.withConnection {
      implicit connection =>

        SQL(
          """
            update version
            set name = {name}, parent = {parent}
            where id = {id}
          """
        ).on(
          'id -> id,
          'name -> version.name,
          'parent -> version.parent
        ).executeUpdate()

        //remove all version cache
        Cache.set(allVersionsCacheKey, None, 1)

        //store object in cache for later retrieval.
        val cached = findById(id).get
        Cache.set(versionCacheKey + id, cached.copy(name = version.name, parent = version.parent), 60*60)
    }
  }

  /**
   * Returns a page of Versions
   *
   * @param page Page to display
   * @param pageSize Number of elements per page
   * @param orderBy property used for sorting
   * @param filter Filter applied on the elements
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%") = {
    val offset = pageSize * page
    val mode = if (orderBy > 0) "ASC NULLS FIRST" else "DESC NULLS LAST"

    Logger.debug("Version.list with params: page[%d] pageSize[%d] orderBy[%d] filter[%s] order[%s]".format(page, pageSize, orderBy, filter, mode))

    DB.withConnection {
      implicit connection =>

      // An explanation on the format applied to the SQL String: for some reason the JDBC driver for postgresql doesn't like it when you replace the
      // order by param in a statement. It gets ignored. So we have to provide it in the query before the driver processes the statement.
      // Now you'll be screaming "SQL INJECTION". Relax. Although it could happen, orderBy is an integer (which we turn into abs value for more safety).
      // If you try to call the controller that provides orderBy with a string, Play returns a 404 error. So only integers are allowed. And if there is no
      // column corresponding to the given integer, the order by is ignored. So, not ideal, but not so bad.
        val versions = SQL(
          """
            select v1.id, v1.name, v1.parent, parent.name  as "pname"
            from version v1
            left join version parent on v1.parent = parent.id
            where v1.name ilike {filter}
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'filter -> filter
        ).as(Version.withParent *)

        val totalRows = SQL(
          """
            select count(*) from version
            where name like {filter}
          """
        ).on(
          'filter -> filter
        ).as(scalar[Long].single)

        Page(versions, page, offset, totalRows)
    }

  }

}
