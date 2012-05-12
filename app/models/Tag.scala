package models

import anorm.SqlParser._
import play.api.cache.Cache
import play.api.db.DB
import anorm._
import play.api.Play.current
import play.Logger
import com.github.mumoshu.play2.memcached.MemcachedPlugin

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 21/04/12
 * Time: 17:34
 * Tag management
 */

case class Tag(id: Pk[Long] = NotAssigned, name: String)
case class TagMerge(sourceid: Long, tags: List[String])

/**
 * Tag object
 */
object Tag {


  val tagCacheKey = "Tg"
  val tagNameCacheKey = tagCacheKey + "N"
  val tagDemoCacheKey = tagCacheKey + "d"
  val allTagsCacheKey = tagCacheKey + "All"
  //to avoid duplication
  val tagsByDemoQuery = """
              select distinct t.name as "name"
              from tagdemo td
              left join tag t on td.tag = t.id
              where td.demo = {demoid}
            """

  // Parsers

  /**
   * Parse a Tag from a ResultSet
   */
  val simple = {
      get[Pk[Long]]("tag.id") ~
      get[String]("tag.name") map {
      case id ~ name => Tag(id, name)
    }
  }

  // Queries

  /**
   * Retrieve a Tag by the id.
   *
   * @param id the id of the tag to retrieve
   */
  def findById(id: Long): Option[Tag] = {
    Cache.getOrElse(tagCacheKey + id, 60*60) {
      DB.withConnection {
        implicit connection =>
          SQL("select * from tag where id = {id}").on('id -> id).as(Tag.simple.singleOpt)
      }
    }
  }

  /**
   * Retrieve a Tag by the name
   *
   * @param name the name of the tag to retrieve
   */
  def findByName(name: String): Option[Tag] = {
    Cache.getOrElse(tagNameCacheKey + name, 60*60) {
      DB.withConnection {
        implicit connection =>
          SQL("select * from tag where name = {name}").on('name -> name).as(Tag.simple.singleOpt)
      }
    }
  }

  /**
   * Retrieve a list of tag names by the demo associated to them
   * As we may have introduced some duplications on edit, we do a filtering to return unique results
   *
   * @param demoid id of the demo that references the tags
   */
  def findByDemo(demoid: Long) : List[String] = {
    Cache.getOrElse(tagDemoCacheKey + demoid, 60*60) {
      DB.withConnection {
        implicit connection =>
          SQL(
            tagsByDemoQuery
          ).on('demoid -> demoid).as(str("name") *)
      }
    }
  }

  /**
   * Retrieve all Tags in a list
   */
  def allTags(): List[Tag] = {
    Cache.getOrElse(allTagsCacheKey, 60) {
      DB.withConnection {
        implicit connection =>
          SQL("select id, name from tag").as(Tag.simple *)
      }
    }
  }

  /**
   * Retrieve all tags for a select in a template
   * Not cached as it is only used by admin
   */
  def allSelect(): Map[String, String] = {
      DB.withConnection {
        implicit connection =>
          SQL("select id, name from tag").as(long("id") ~ str("name") map(flatten) *).groupBy(_._1).map { case (k,v) => (k.toString,v.foldLeft("")((s,t) => s + t._2))}
      }
  }

  /**
   * Adds tags in relation to the given demo, creating the tag if required
   *
   * @param tags the list of tag names
   * @param demoid the id of the demo
   * @param connection the db connection, required to avoid nested connections (caused issues)
   */
  def addToDemo(tags: List[String],  demoid: Long)(implicit connection: java.sql.Connection): Any = {
        SQL(
          """
            delete from tagdemo where demo = {demoid}
          """
        ).on(
          'demoid -> demoid
        ).executeUpdate()

        tags.map { name => addToDemo(name, demoid) }

        //after adding tags, we update cache
        val newList = SQL(
          tagsByDemoQuery
        ).on('demoid -> demoid).as(str("name") *)

        Cache.set(tagDemoCacheKey + demoid, newList, 60*60);
  }


  /**
   * Adds a new tag in relation to the given demo, creating the tag if required.
   * The tag must not be linked to the demo
   * Called from addToDemo(List[String], Long)
   *
   * @param tag the tag name
   * @param demoid the id of the demo
   * @param connection the db connection, required to avoid nested connections (caused issues)
   */   
  private def addToDemo(tag: String,  demoid: Long)(implicit connection: java.sql.Connection): Any = {
        val tagId = findByName(tag) match {
          case Some(tag) => tag.id.get
          case None => create(Tag(name=tag)).id.get
        }
        
        SQL(
          """
            insert into tagdemo (demo, tag) values ({demoid}, {tagid})
          """
        ).on(
          'demoid -> demoid,
          'tagid -> tagId
        ).executeUpdate()
  }


  /**
   * Returns a page of Tags
   *
   * @param page Page to display
   * @param pageSize Number of elements per page
   * @param orderBy property used for sorting
   * @param filter Filter applied on the elements
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%") = {
    val offset = pageSize * page
    val mode = if (orderBy > 0) "ASC NULLS FIRST" else "DESC NULLS LAST"

    Logger.debug("Tag.list with params: page[%d] pageSize[%d] orderBy[%d] filter[%s] order[%s]".format(page, pageSize, orderBy, filter, mode))

    DB.withConnection {
      implicit connection =>

      // An explanation on the format applied to the SQL String: for some reason the JDBC driver for postgresql doesn't like it when you replace the
      // order by param in a statement. It gets ignored. So we have to provide it in the query before the driver processes the statement.
      // Now you'll be screaming "SQL INJECTION". Relax. Although it could happen, orderBy is an integer (which we turn into abs value for more safety).
      // If you try to call the controller that provides orderBy with a string, Play returns a 404 error. So only integers are allowed. And if there is no
      // column corresponding to the given integer, the order by is ignored. So, not ideal, but not so bad.
        val versions = SQL(
          """
            select *
            from tag            
            where name ilike {filter}
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'filter -> filter
        ).as(Tag.simple *)

        val totalRows = SQL(
          """
            select count(*) from tag
            where name like {filter}
          """
        ).on(
          'filter -> filter
        ).as(scalar[Long].single)

        Page(versions, page, offset, totalRows, pageSize)
    }

  }

  /**
   * Insert a new Tag
   *
   * @param tag the tag values
   * @return the tag created (including id)
   */
  def create(tag: Tag) = {
    DB.withConnection {
      implicit connection =>
      //we use many default values from the db
        val id = SQL(
          """
            insert into tag (name) values ({name})
            returning id
          """
        ).on(
          'name -> tag.name
        ).as(long("id").single)

        //store object in cache for later retrieval
        val newTag = tag.copy(id = Id(id))
        Cache.set(tagCacheKey + id, newTag, 60*60)
        Cache.set(tagNameCacheKey + newTag.name, newTag, 60*60)
        newTag
    }
  }

  /**
   * Updates the tag details in the database
   *
   * @param id the id of the tag to update
   * @param tag the details to update
   */
  def update(id: Long, tag: Tag) = {
    DB.withConnection {
      implicit connection =>

        SQL(
          """
            update tag
            set name = {name}
            where id = {id}
          """
        ).on(
          'id -> id,
          'name -> tag.name
        ).executeUpdate()


        //store object in cache for later retrieval.
        val cached = findById(id).get
        val copy = cached.copy(name = tag.name)
        Cache.set(tagCacheKey + id, copy, 60*60)
        Cache.set(tagNameCacheKey + tag.name, copy, 60*60)
    }
  }

  /**
   * Deletes the tag with the given id
   * @param id the id of the tag to delete
   */
  def delete(id: Long) = {
    DB.withConnection {
      implicit connection =>
        val cached = findById(id).get
        SQL(
          """
            delete from tag
            where id = {id}
          """
        ).on(
          'id -> id
        ).executeUpdate()

        //removes from cache. We don't replace by None as a similar tag may be created right now, better delete from cache
        //TODO: use standard Cache method when added
        play.api.Play.current.plugin[MemcachedPlugin].get.api.remove(tagCacheKey + cached.name)
        play.api.Play.current.plugin[MemcachedPlugin].get.api.remove(tagNameCacheKey + cached.name)
    }
  }


  /**
   * Replaces the tags in the given sequence by the given tag
   * @param mergeDetails contains the data to replace the tags
   */
  def merge(mergeDetails: TagMerge) = {
    DB.withTransaction {
      implicit connection =>
        //TODO: replace on projects

        //remove from cache  and merge in demos
        mergeDetails.tags.map { name =>
          findByName(name) match {
            case Some(tag) =>
              Cache.set(tagNameCacheKey + tag.name, None, 60)
              Cache.set(tagCacheKey + tag.id, None, 60)
              SQL(
                """
                  update tagdemo td set td.tag = {newId}
                  where td.tag = {oldId}
                """
              ).on(
                'newId -> mergeDetails.sourceid,
                'oldId -> tag.id
              )
            case _ =>  //tag not in cache, ignore
          }
        }

        //remove duplicates from tagdemo table
        SQL(
          """
            delete from tagdemo t1 using tagdemo t2
            where t1.demo=t2.demo and t1.tag = t2.tag and t1.id < t2.id
          """
        ).executeUpdate()

        //remove from tags table
        val names = "('" + mergeDetails.tags.mkString("','") +"')"
        SQL(
          """
            delete from tag
            where name in %s
          """.format(names)
        ).executeUpdate()

        //clean general list
        val all = allTags().filterNot(mergeDetails.tags.contains(_))
        Cache.set(allTagsCacheKey, all, 60)
    }
  }

}
