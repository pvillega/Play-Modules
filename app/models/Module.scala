package models

import java.util.Date
import anorm.SqlParser._
import play.api.cache.Cache
import play.api.db.DB
import anorm._
import play.Logger
import java.math.BigDecimal
import play.api.Play.current
import com.github.mumoshu.play2.memcached.MemcachedPlugin

/**
 * Created with IntelliJ IDEA.
 * User: pvillega
 * Date: 12/05/12
 * Time: 17:22
 * Manages Play modules
 */

case class Module(id: Pk[Long] = NotAssigned, name: String, version: Long, author: Long = -1, url: String, description: Option[String] = None, positive: Int = 0, negative: Int = 0, updated: Date = new Date, created: Date = new Date, tags: List[String] = Nil)

case class ModuleView(id: Long, name: String, score: BigDecimal, showScore: Int, version: String, aid: Long, publisher: String, url: String, description: Option[String] = None, updated: Date, tags: List[String] = Nil)


/**
 * Module object
 */
object Module {

  val modCacheKey = "Mod"
  val modViewCacheKey = modCacheKey + "V"

  // Parsers

  /**
   * Parse a Module from a ResultSet
   */
  val simple = {
    get[Pk[Long]]("plugin.id") ~
      get[String]("plugin.name") ~
      get[Long]("plugin.version") ~
      get[Long]("plugin.author") ~
      get[String]("plugin.url") ~
      get[Option[String]]("plugin.description") ~
      get[Int]("plugin.positive") ~
      get[Int]("plugin.negative") ~
      get[Date]("plugin.updated") ~
      get[Date]("plugin.created") map {
      case id ~ name ~ version ~ author ~ url ~ description ~ positive ~ negative ~ updated ~ created => Module(id, name, version, author, url, description, positive, negative, updated, created)
    }
  }

  val simpleView = {
    get[Long]("id") ~
      get[String]("name") ~
      get[BigDecimal]("score") ~
      get[Int]("showScore") ~
      get[String]("url") ~
      get[Option[String]]("description") ~
      get[String]("version") ~
      get[Long]("aid") ~
      get[String]("publisher") ~
      get[Date]("updated") map {
      case id ~ name ~ score ~ showScore ~ url ~ description ~ version ~ aid ~ publisher ~ updated => ModuleView(id, name, score, showScore, version, aid, publisher, url, description, updated)
    }
  }

  // Queries

  /**
   * Retrieve a Module from the id.
   *
   * @param id the id of the module to retrieve
   */
  def findById(id: Long): Option[Module] = {
    Cache.getOrElse(modCacheKey + id, 60*60) {
      DB.withConnection {
        implicit connection =>
          SQL("select * from plugin where id = {id}").on('id -> id).as(Module.simple.singleOpt) match {
            case Some(mod) =>
              val list = Tag.findByModule(mod.id.get)
              Some(mod.copy(tags = list))
            case None  => None
          }
      }
    }
  }

  /**
   * Retrieve a Module from the id, returning also the version and author name
   * Score sorting algorithm from http://evanmiller.org/how-not-to-sort-by-average-rating.html
   *
   * @param id the id of the module to retrieve
   */
  def findByIdWithVersion(id: Long): Option[ModuleView] = {
    Cache.getOrElse(modViewCacheKey + id, 60) {
      DB.withConnection {
        implicit connection =>
          SQL(
            """
              select pl.id as "id", pl.name as "name",
              ((pl.positive + 1.9208) / (pl.positive + pl.negative) -
                   1.96 * SQRT((pl.positive * pl.negative) / (pl.positive + pl.negative) + 0.9604) /
                          (pl.positive + pl.negative)) / (1 + 3.8416 / (pl.positive + pl.negative)) as "score",
              (pl.positive - pl.negative) as "showScore",
              pl.url as "url", pl.description as "description", pl.updated as "updated", v.name as "version", p.id as "aid", p.name as "publisher"
              from plugin pl
              left join version v on v.id = pl.version
              left join publisher p on p.id = pl.author
              where pl.id = {id}
            """
          ).on('id -> id).as(Module.simpleView.singleOpt) match {
            case Some(mod) =>
              val list = Tag.findByModule(mod.id)
              Some(mod.copy(tags = list))
            case None  => None
          }
      }
    }
  }

  /**
   * Insert a new Module.
   *
   * @param mod the module values
   * @param userid id of the author of the module
   * @return the version created (including id)
   */
  def create(mod: Module, userid: Long) = {
    DB.withTransaction {
      implicit connection =>
      //we use many default values from the db
        val id = SQL(
          """
            insert into plugin (name, version, author, url, description)
            values ({name}, {version}, {author}, {url}, {description})
            returning id
          """
        ).on(
          'name -> mod.name,
          'version -> mod.version,
          'author -> userid,
          'url -> mod.url,
          'description -> mod.description
        ).as(long("id").single)

        //add tags to the module
        Tag.addToModule(mod.tags, id)

        //author automatically votes his own creation +1
        SQL(
          """
            insert into voteplugin (author, plugin, vote) values ({author}, {mod}, 1)
          """
        ).on(
          'mod -> id,
          'author -> userid
        ).executeUpdate()

        //store object in cache for later retrieval
        val newMod = mod.copy(id = Id(id), author = userid)
        Cache.set(modCacheKey + id, newMod, 60*60)
        newMod
    }
  }


  /**
   * Updates the module details in the database
   *
   * @param id the id of the module to update
   * @param userid the id of the user editing the module
   * @param mod the details to update
   */
  def update(id: Long, userid: Long, mod: Module) = {
    DB.withTransaction {
      implicit connection =>
        val date = new Date()
        SQL(
          """
            update plugin
            set name = {name}, version = {version}, url = {url}, description = {description}, updated = {updated}
            where id = {id}  and author = {author}
          """
        ).on(
          'id -> id,
          'name -> mod.name,
          'version -> mod.version,
          'author -> userid,
          'url -> mod.url,
          'description -> mod.description,
          'updated -> date
        ).executeUpdate()

        //add tags to the module
        Tag.addToModule(mod.tags, id)

        //store object in cache for later retrieval. This should be in cache already so this should be quick
        val cached = findById(id).get
        val copy = cached.copy(name = mod.name, version = mod.version, url = mod.url, description = mod.description, tags = mod.tags, updated = date)
        Cache.set(modCacheKey + id, copy, 60*60)
    }
  }

  /**
   * Deletes the module with the given id
   * @param id the id of the module to delete
   * @param userid the id of the user deleting the demo
   */
  def delete(id: Long, userid: Long) = {
    DB.withConnection {
      implicit connection =>
        SQL(
          """
            delete from plugin
            where id = {id}  and author = {author}
          """
        ).on(
          'id -> id,
          'author -> userid
        ).executeUpdate()

        //removes from cache
        Cache.set(modCacheKey + id, None, 60)
    }
  }

  /**
   * Obtains the vote option of the current user in the current module
   * @param userid the id of the user
   * @param modid the id of the module
   */
  def getUserVote(userid: Option[String], modid: Long) : Option[Int] = {
    userid match {
      case Some(user) =>
        DB.withConnection {
            implicit connection =>
          SQL(
            """
                select vote from voteplugin
                where author = {author} and plugin = {modid}
            """
          ).on(
            'modid -> modid,
            'author -> user.toLong
          ).as(int("vote").singleOpt)
        }

      case _ => None
    }
  }

  /**
   * Stores the vote of a user for the given module
   * @param userid id of the user voting
   * @param modid id of the module for which the user voted
   * @param vote value of the vote (+1/-1)
   * @param oldVote previous value of the vote (+1/-1/0)
   */
  def vote(userid: Long, modid: Long, vote: Int, oldVote: Int) = {
    DB.withTransaction {
      implicit connection =>
        //new insert or update
        val voteQuery = if (oldVote == 0) {
          """
            insert into voteplugin (author, plugin, vote) values ({author}, {mod}, {vote})
          """
        } else {
          """
            update voteplugin set vote = {vote}
            where author = {author} and plugin = {mod}
          """
        }
        SQL(
          voteQuery
        ).on(
          'mod -> modid,
          'author -> userid,
          'vote -> vote
        ).executeUpdate()

        //update module table
        SQL(
          """
            update plugin set positive = (select count(*) from voteplugin where plugin = {id} and vote > 0),
             negative = (select count(*) from voteplugin where plugin = {id} and vote < 0)
            where id = {id}
          """
        ).on(
          'id -> modid
        ).executeUpdate()

        //updates cache to see vote effect on next request
        //TODO: update when Cache api adds this option
        play.api.Play.current.plugin[MemcachedPlugin].get.api.remove(modViewCacheKey + modid)
    }
  }

  /**
   * Returns a page of Module
   * Score sorting algorithm from http://evanmiller.org/how-not-to-sort-by-average-rating.html
   *
   * @param page Page to display
   * @param pageSize Number of elements per page
   * @param orderBy property used for sorting
   * @param nameFilter Filter applied on the elements
   * @param versionFilter filter applied on version
   * @param tagFilter filter applied on tags
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, nameFilter: String = "%", versionFilter : Long = -1, tagFilter: List[String] = Nil) = {
    val offset = pageSize * page
    val mode = if (orderBy > 0) "ASC NULLS FIRST" else "DESC NULLS LAST"

    Logger.debug("Module.list with params: page[%d] pageSize[%d] orderBy[%d] filter[%s | %s | %s] order[%s]".format(page, pageSize, orderBy, nameFilter, versionFilter, tagFilter, mode))

    DB.withConnection {
      implicit connection =>
      //we can't use IN on anorm directly, so we have to resort to an insert into the string if required. We don't add the join if not required
        val (tagJoin , tags) = tagFilter match {
          case l: List[_] if !l.isEmpty=> ("left join tagplugin td on td.plugin = pl.id left join tag t on td.tag = t.id", "and t.name in ('"+ l.mkString("','") +"')")
          case _ => ("","")
        }

        // An explanation on the format applied to the SQL String: for some reason the JDBC driver for postgresql doesn't like it when you replace the
        // order by param in a statement. It gets ignored. So we have to provide it in the query before the driver processes the statement.
        // Now you'll be screaming "SQL INJECTION". Relax. Although it could happen, orderBy is an integer (which we turn into abs value for more safety).
        // If you try to call the controller that provides orderBy with a string, Play returns a 404 error. So only integers are allowed. And if there is no
        // column corresponding to the given integer, the order by is ignored. So, not ideal, but not so bad.
        val mods = SQL(
          """
            select distinct pl.id as "id", pl.name as "name",
            ((pl.positive + 1.9208) / (pl.positive + pl.negative) -
                   1.96 * SQRT((pl.positive * pl.negative) / (pl.positive + pl.negative) + 0.9604) /
                          (pl.positive + pl.negative)) / (1 + 3.8416 / (pl.positive + pl.negative)) as "score",
            (pl.positive - pl.negative) as "showScore",
            pl.url as "url", pl.description as "description", pl.updated as "updated", version.name as "version", publisher.id as "aid", publisher.name as "publisher"
            from plugin pl
            left join version  on version.id = pl.version
            left join publisher on publisher.id = pl.author
            %s
            where pl.name ilike {nameFilter}
            and ({versionFilter} <= 0 or pl.version = {versionFilter} or version.parent = {versionFilter})
            %s
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(tagJoin, tags, scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'nameFilter -> nameFilter,
          'versionFilter -> versionFilter
        ).as(Module.simpleView *)  //Vote is set to 0 as it won't be used, to avoid a join

        val totalRows = SQL(
          """
            select count(*) from plugin pl
            left join version  on version.id = pl.version
            %s
            where pl.name like {nameFilter}
            and ({versionFilter} <= 0 or pl.version = {versionFilter}  or version.parent = {versionFilter})
            %s
          """.format(tagJoin, tags)
        ).on(
          'nameFilter -> nameFilter,
          'versionFilter -> versionFilter
        ).as(scalar[Long].single)

        Page(mods, page, offset, totalRows, pageSize)
    }

  }

  /**
   * Returns a page of Module linked to a user
   * Score sorting algorithm from http://evanmiller.org/how-not-to-sort-by-average-rating.html
   *
   * @param page Page to display
   * @param pageSize Number of elements per page
   * @param orderBy property used for sorting
   * @param userId the id of the user that owns the demo
   */
  def listByUser(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, userId: Long) = {
    val offset = pageSize * page
    val mode = if (orderBy > 0) "ASC NULLS FIRST" else "DESC NULLS LAST"

    if(Logger.isDebugEnabled){
      Logger.debug("Module.listByUser with params: page[%d] pageSize[%d] orderBy[%d] order[%s]".format(page, pageSize, orderBy, mode))
    }

    DB.withConnection {
      implicit connection =>

      // An explanation on the format applied to the SQL String: for some reason the JDBC driver for postgresql doesn't like it when you replace the
      // order by param in a statement. It gets ignored. So we have to provide it in the query before the driver processes the statement.
      // Now you'll be screaming "SQL INJECTION". Relax. Although it could happen, orderBy is an integer (which we turn into abs value for more safety).
      // If you try to call the controller that provides orderBy with a string, Play returns a 404 error. So only integers are allowed. And if there is no
      // column corresponding to the given integer, the order by is ignored. So, not ideal, but not so bad.
        val mods = SQL(
          """
            select pl.id as "id", pl.name as "name",
            ((pl.positive + 1.9208) / (pl.positive + pl.negative) -
                   1.96 * SQRT((pl.positive * pl.negative) / (pl.positive + pl.negative) + 0.9604) /
                          (pl.positive + pl.negative)) / (1 + 3.8416 / (pl.positive + pl.negative)) as "score",
            (pl.positive - pl.negative) as "showScore",
            pl.url as "url", pl.description as "description", pl.updated as "updated", version.name as "version", publisher.id as "aid", publisher.name as "publisher"
            from plugin pl
            left join version  on version.id = pl.version
            left join publisher on publisher.id = pl.author
            where pl.author = {userid}
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'userid -> userId
        ).as(Module.simpleView *)       //Vote is set to 0 as it won't be used, to avoid a join

        val totalRows = SQL(
          """
            select count(*) from plugin
            where author = {userid}
          """
        ).on(
          'userid -> userId
        ).as(scalar[Long].single)

        Page(mods, page, offset, totalRows, pageSize)
    }

  }

}
