package models

import anorm.SqlParser._
import play.api.cache.Cache
import play.api.db.DB
import anorm._
import play.api.Play.current
import play.Logger
import java.util.Date
import java.math.BigDecimal
import com.github.mumoshu.play2.memcached.MemcachedPlugin

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 25/03/12
 * Time: 15:45
 * Manages Demo entities
 */

case class Demo(id: Pk[Long] = NotAssigned, name: String, version: Long, author: Long = -1, codeurl: String, demourl: Option[String] = None, description: Option[String] = None, positive: Int = 0, negative: Int = 0, created: Date = new Date, tags: List[String] = Nil)

case class DemoView(id: Long, name: String, score: BigDecimal, showScore: Int, version: String, aid: Long, publisher: String, codeurl: String, demourl: Option[String] = None, description: Option[String] = None, tags: List[String] = Nil)


/**
 * Demo object
 */
object Demo {

  val demoCacheKey = "Dmo"
  val demoViewCacheKey = demoCacheKey + "V"

  // Parsers

  /**
   * Parse a Demo from a ResultSet
   */
  val simple = {
    get[Pk[Long]]("demo.id") ~
      get[String]("demo.name") ~
      get[Long]("demo.version") ~
      get[Long]("demo.author") ~
      get[String]("demo.codeurl") ~
      get[Option[String]]("demo.demourl") ~
      get[Option[String]]("demo.description") ~
      get[Int]("demo.positive") ~
      get[Int]("demo.negative") ~
      get[Date]("demo.created") map {
      case id ~ name ~ version ~ author ~ codeurl ~ demourl ~ description ~ positive ~ negative ~ created => Demo(id, name, version, author, codeurl, demourl, description, positive, negative, created)
    }
  }

  val simpleView = {
    get[Long]("id") ~
      get[String]("name") ~
      get[BigDecimal]("score") ~
      get[Int]("showScore") ~
      get[String]("demo.codeurl") ~
      get[Option[String]]("demo.demourl") ~
      get[Option[String]]("demo.description") ~
      get[String]("version") ~
      get[Long]("aid") ~
      get[String]("publisher") map {
      case id ~ name ~ score ~ showScore ~ codeurl ~ demourl ~ description ~ version ~ aid ~ publisher => DemoView(id, name, score, showScore, version, aid, publisher, codeurl, demourl, description)
    }
  }

  // Queries

  /**
   * Retrieve a Demo from the id.
   *
   * @param id the id of the demo to retrieve
   */
  def findById(id: Long): Option[Demo] = {
    Cache.getOrElse(demoCacheKey + id, 60*60) {
      DB.withConnection {
        implicit connection =>
          SQL("select * from demo where id = {id}").on('id -> id).as(Demo.simple.singleOpt) match {
            case Some(demo) =>
              val list = Tag.findByDemo(demo.id.get)
              Some(demo.copy(tags = list))
            case None  => None
          }
      }
    }
  }

  /**
   * Retrieve a Demo from the id, returning also the version and author name
   * Score sorting algorithm from http://evanmiller.org/how-not-to-sort-by-average-rating.html
   *
   * @param id the id of the demo to retrieve
   */
  def findByIdWithVersion(id: Long): Option[DemoView] = {
    Cache.getOrElse(demoViewCacheKey + id, 60) {
      DB.withConnection {
        implicit connection =>
          SQL(
            """
              select d.id as "id", d.name as "name",
              ((d.positive + 1.9208) / (d.positive + d.negative) -
                   1.96 * SQRT((d.positive * d.negative) / (d.positive + d.negative) + 0.9604) /
                          (d.positive + d.negative)) / (1 + 3.8416 / (d.positive + d.negative)) as "score",
              (d.positive - d.negative) as "showScore",
              d.codeurl, d.demourl, d.description, v.name as "version", p.id as "aid", p.name as "publisher"
              from demo d
              left join version v on v.id = d.version
              left join publisher p on p.id = d.author
              where d.id = {id}
            """
          ).on('id -> id).as(Demo.simpleView.singleOpt) match {
            case Some(demo) =>
              val list = Tag.findByDemo(demo.id)
              Some(demo.copy(tags = list))
            case None  => None
          }
      }
    }
  }

  /**
   * Insert a new Demo.
   *
   * @param demo the demo values
   * @param userid id of the author of the demo
   * @return the version created (including id)
   */
  def create(demo: Demo, userid: Long) = {
    DB.withTransaction {
      implicit connection =>
      //we use many default values from the db
        val id = SQL(
          """
            insert into demo (name, version, author, codeurl, demourl, description)
            values ({name}, {version}, {author}, {codeurl}, {demourl}, {description})
            returning id
          """
        ).on(
          'name -> demo.name,
          'version -> demo.version,
          'author -> userid,
          'codeurl -> demo.codeurl,
          'demourl -> demo.demourl,
          'description -> demo.description
        ).as(long("id").single)

        //add tags to the demo
        Tag.addToDemo(demo.tags, id)

        //author automatically votes his own creation +1
        SQL(
          """
            insert into votedemo (author, demo, vote) values ({author}, {demo}, 1)
          """
        ).on(
          'demo -> id,
          'author -> userid
        ).executeUpdate()

        //store object in cache for later retrieval
        val newDemo = demo.copy(id = Id(id), author = userid)
        Cache.set(demoCacheKey + id, newDemo, 60*60)
        newDemo
    }
  }


  /**
   * Updates the demo details in the database
   *
   * @param id the id of the demo to update
   * @param userid the id of the user editing the demo
   * @param demo the details to update
   */
  def update(id: Long, userid: Long, demo: Demo) = {
    DB.withTransaction {
      implicit connection =>

        SQL(
          """
            update demo
            set name = {name}, version = {version}, codeurl = {codeurl}, demourl = {demourl}, description = {description}
            where id = {id}  and author = {author}
          """
        ).on(
          'id -> id,
          'name -> demo.name,
          'version -> demo.version,
          'author -> userid,
          'codeurl -> demo.codeurl,
          'demourl -> demo.demourl,
          'description -> demo.description
        ).executeUpdate()

        //add tags to the demo
        Tag.addToDemo(demo.tags, id)

        //store object in cache for later retrieval. This user should be in cache already so this should be quick
        val cached = findById(id).get
        val copy = cached.copy(name = demo.name, version = demo.version, codeurl = demo.codeurl, demourl = demo.demourl, description = demo.description, tags = demo.tags)
        Cache.set(demoCacheKey + id, copy, 60*60)
    }
  }

  /**
   * Deletes the demo with the given id
   * @param id the id of the demo to delete
   * @param userid the id of the user deleting the demo
   */
  def delete(id: Long, userid: Long) = {
    DB.withTransaction {
      implicit connection =>
        SQL(
          """
            delete from demo
            where id = {id}  and author = {author}
          """
        ).on(
          'id -> id,
          'author -> userid
        ).executeUpdate()

        //removes from cache
        Cache.set(demoCacheKey + id, None, 60)
    }
  }

  /**
   * Obtains the vote option of the current user in the current demo
   * @param userid the id of the user
   * @param demoid the id of the demo
   */
  def getUserVote(userid: Option[String], demoid: Long) : Option[Int] = {
    userid match {
      case Some(user) =>
        DB.withConnection {
          implicit connection =>
            SQL(
              """
                select vote from votedemo
                where author = {author} and demo = {demoid}
              """
            ).on(
              'demoid -> demoid,
              'author -> user.toLong
            ).as(int("vote").singleOpt)
        }

      case _ => None
    }
  }

  /**
   * Stores the vote of a user for the given demo
   * @param userid id of the user voting
   * @param demoid id of the demo for which the user voted
   * @param vote value of the vote (+1/-1)
   * @param oldVote previous value of the vote (+1/-1/0)
   */
  def vote(userid: Long, demoid: Long, vote: Int, oldVote: Int) = {
    DB.withTransaction {
      implicit connection =>
      //new insert or update
        val voteQuery = if (oldVote == 0) {
          """
            insert into votedemo (author, demo, vote) values ({author}, {demo}, {vote})
          """
        } else {
          """
            update votedemo set vote = {vote}
            where author = {author} and demo = {demo}
          """
        }
        SQL(
          voteQuery
        ).on(
          'demo -> demoid,
          'author -> userid,
          'vote -> vote
        ).executeUpdate()

        //upate demo table
        SQL(
          """
            update demo set positive = (select count(*) from votedemo where demo = {id} and vote > 0),
             negative = (select count(*) from votedemo where demo = {id} and vote < 0)
            where id = {id}  
          """
        ).on(
          'id -> demoid
        ).executeUpdate()

        //updates cache to see vote effect on next request
        //TODO: update when Cache api adds this option
        play.api.Play.current.plugin[MemcachedPlugin].get.api.remove(demoViewCacheKey + demoid)
    }
  }

  /**
   * Returns a page of Demo
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

    Logger.debug("Demo.list with params: page[%d] pageSize[%d] orderBy[%d] filter[%s | %s | %s] order[%s]".format(page, pageSize, orderBy, nameFilter, versionFilter, tagFilter, mode))

    DB.withConnection {
      implicit connection =>
        //we can't use IN on anorm directly, so we have to resort to an insert into the string if required. We don't add the join if not required
        val (tagJoin , tags) = tagFilter match {
            case l: List[_] if !l.isEmpty=> ("left join tagdemo td on td.demo = demo.id left join tag t on td.tag = t.id", "and t.name in ('"+ l.mkString("','") +"')")
            case _ => ("","")
        }

      // An explanation on the format applied to the SQL String: for some reason the JDBC driver for postgresql doesn't like it when you replace the
      // order by param in a statement. It gets ignored. So we have to provide it in the query before the driver processes the statement.
      // Now you'll be screaming "SQL INJECTION". Relax. Although it could happen, orderBy is an integer (which we turn into abs value for more safety).
      // If you try to call the controller that provides orderBy with a string, Play returns a 404 error. So only integers are allowed. And if there is no
      // column corresponding to the given integer, the order by is ignored. So, not ideal, but not so bad.
        val demos = SQL(
          """
            select distinct demo.id as "id", demo.name as "name",
            ((demo.positive + 1.9208) / (demo.positive + demo.negative) -
                   1.96 * SQRT((demo.positive * demo.negative) / (demo.positive + demo.negative) + 0.9604) /
                          (demo.positive + demo.negative)) / (1 + 3.8416 / (demo.positive + demo.negative)) as "score",
            (demo.positive - demo.negative) as "showScore",
            demo.codeurl, demo.demourl, demo.description, version.name as "version", publisher.id as "aid", publisher.name as "publisher"
            from demo
            left join version  on version.id = demo.version
            left join publisher on publisher.id = demo.author
            %s
            where demo.name ilike {nameFilter}
            and ({versionFilter} <= 0 or demo.version = {versionFilter} or version.parent = {versionFilter})
            %s
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(tagJoin, tags, scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'nameFilter -> nameFilter,
          'versionFilter -> versionFilter
        ).as(Demo.simpleView *)  //Vote is set to 0 as it won't be used, to avoid a join

        val totalRows = SQL(
          """
            select count(*) from demo
            left join version  on version.id = demo.version
            %s
            where demo.name like {nameFilter}
            and ({versionFilter} <= 0 or demo.version = {versionFilter} or version.parent = {versionFilter})
            %s
          """.format(tagJoin, tags)
        ).on(
          'nameFilter -> nameFilter,
          'versionFilter -> versionFilter
        ).as(scalar[Long].single)

        Page(demos, page, offset, totalRows, pageSize)
    }

  }

  /**
   * Returns a page of Demo linked to a user
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
      Logger.debug("Demo.listByUser with params: page[%d] pageSize[%d] orderBy[%d] order[%s]".format(page, pageSize, orderBy, mode))
    }

    DB.withConnection {
      implicit connection =>

      // An explanation on the format applied to the SQL String: for some reason the JDBC driver for postgresql doesn't like it when you replace the
      // order by param in a statement. It gets ignored. So we have to provide it in the query before the driver processes the statement.
      // Now you'll be screaming "SQL INJECTION". Relax. Although it could happen, orderBy is an integer (which we turn into abs value for more safety).
      // If you try to call the controller that provides orderBy with a string, Play returns a 404 error. So only integers are allowed. And if there is no
      // column corresponding to the given integer, the order by is ignored. So, not ideal, but not so bad.
        val demos = SQL(
          """
            select demo.id as "id", demo.name as "name",
            ((demo.positive + 1.9208) / (demo.positive + demo.negative) -
                   1.96 * SQRT((demo.positive * demo.negative) / (demo.positive + demo.negative) + 0.9604) /
                          (demo.positive + demo.negative)) / (1 + 3.8416 / (demo.positive + demo.negative)) as "score",
            (demo.positive - demo.negative) as "showScore",
            demo.codeurl, demo.demourl, demo.description, version.name as "version", publisher.id as "aid", publisher.name as "publisher"
            from demo
            left join version  on version.id = demo.version
            left join publisher on publisher.id = demo.author
            where demo.author = {userid}
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'userid -> userId
        ).as(Demo.simpleView *)       //Vote is set to 0 as it won't be used, to avoid a join

        val totalRows = SQL(
          """
            select count(*) from demo
            where author = {userid}
          """
        ).on(
          'userid -> userId
        ).as(scalar[Long].single)

        Page(demos, page, offset, totalRows, pageSize)
    }

  }

}
