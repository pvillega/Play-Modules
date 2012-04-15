package models

import anorm.SqlParser._
import play.api.cache.Cache
import play.api.db.DB
import anorm._
import play.api.Play.current
import play.Logger
import java.util.Date
import java.math.BigDecimal

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 25/03/12
 * Time: 15:45
 * Manages Demo entities
 */

case class Demo(id: Pk[Long] = NotAssigned, name: String, version: Long, author: Long = -1, codeurl: String, demourl: Option[String] = None, description: Option[String] = None, positive: Int = 0, negative: Int = 0, created: Date = new Date)

case class DemoView(id: Long, name: String, score: BigDecimal, showScore: Int, version: String, aid: Long, publisher: String, codeurl: String, demourl: Option[String] = None, description: Option[String] = None, vote: Option[Int] = Some(0))


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
      get[String]("publisher") ~
      get[Option[Int]]("vote") map {
      case id ~ name ~ score ~ showScore ~ codeurl ~ demourl ~ description ~ version ~ aid ~ publisher ~ vote => DemoView(id, name, score, showScore, version, aid, publisher, codeurl, demourl, description, vote)
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
          SQL("select * from demo where id = {id}").on('id -> id).as(Demo.simple.singleOpt)
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
              d.codeurl, d.demourl, d.description, v.name as "version", p.id as "aid", p.name as "publisher", vd.vote as "vote"
              from demo d
              left join version v on v.id = d.version
              left join publisher p on p.id = d.author
              left join votedemo vd on vd.author = d.author and vd.demo = d.id
              where d.id = {id}
            """
          ).on('id -> id).as(Demo.simpleView.singleOpt)

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
    DB.withConnection {
      implicit connection =>
      //we use many default values from the db
        SQL(
          """
            insert into demo (name, version, author, codeurl, demourl, description)
            values ({name}, {version}, {author}, {codeurl}, {demourl}, {description})
          """
        ).on(
          'name -> demo.name,
          'version -> demo.version,
          'author -> userid,
          'codeurl -> demo.codeurl,
          'demourl -> demo.demourl,
          'description -> demo.description
        ).executeUpdate()

        // as per http://wiki.postgresql.org/wiki/FAQ this should not bring race issues
        val id = SQL("select currval(pg_get_serial_sequence('demo', 'id'))").as(scalar[Long].single)

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
   * @param id the id of the version to update
   * @param userid the id of the user editing the demo
   * @param demo the details to update
   */
  def update(id: Long, userid: Long, demo: Demo) = {
    DB.withConnection {
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

        //store object in cache for later retrieval. This user should be in cache already so this should be quick
        val cached = findById(id).get
        Cache.set(demoCacheKey + id, cached.copy(name = demo.name, version = demo.version, codeurl = demo.codeurl, demourl = demo.demourl, description = demo.description), 60*60)
    }
  }

  /**
   * Deletes the demo with the given id
   * @param id the id of the demo to delete
   * @param userid the id of the user deleting the demo
   */
  def delete(id: Long, userid: Long) = {
    DB.withConnection {
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
        Cache.set(demoCacheKey + id, None, 1)
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

        //removes from cache to see vote effect on next request
        val cached = findByIdWithVersion(demoid).get
        Cache.set(demoViewCacheKey + demoid, cached.copy(vote = Some(vote)), 60)
    }
  }

  /**
   * Returns a page of Demo
   * Score sorting algorithm from http://evanmiller.org/how-not-to-sort-by-average-rating.html
   *
   * @param page Page to display
   * @param pageSize Number of elements per page
   * @param orderBy property used for sorting
   * @param filter Filter applied on the elements
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%") = {
    val offset = pageSize * page
    val mode = if (orderBy > 0) "ASC NULLS FIRST" else "DESC NULLS LAST"

    Logger.debug("Demo.list with params: page[%d] pageSize[%d] orderBy[%d] filter[%s] order[%s]".format(page, pageSize, orderBy, filter, mode))

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
            demo.codeurl, demo.demourl, demo.description, version.name as "version", publisher.id as "aid", publisher.name as "publisher", 0 as "vote"
            from demo
            left join version  on version.id = demo.version
            left join publisher on publisher.id = demo.author
            where demo.name ilike {filter}
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'filter -> filter
        ).as(Demo.simpleView *)  //Vote is set to 0 as it won't be used, to avoid a join

        val totalRows = SQL(
          """
            select count(*) from demo
            where name like {filter}
          """
        ).on(
          'filter -> filter
        ).as(scalar[Long].single)

        Page(demos, page, offset, totalRows)
    }

  }

  /**
   * Returns a page of Demo linked to a user
   * Score sorting algorithm from http://evanmiller.org/how-not-to-sort-by-average-rating.html
   *
   * @param page Page to display
   * @param pageSize Number of elements per page
   * @param orderBy property used for sorting
   * @param filter Filter applied on the elements
   * @param userId the id of the user that owns the demo
   */
  def listByUser(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%", userId: Long) = {
    val offset = pageSize * page
    val mode = if (orderBy > 0) "ASC NULLS FIRST" else "DESC NULLS LAST"

    Logger.debug("Demo.listByUser with params: page[%d] pageSize[%d] orderBy[%d] filter[%s] order[%s]".format(page, pageSize, orderBy, filter, mode))

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
            demo.codeurl, demo.demourl, demo.description, version.name as "version", publisher.id as "aid", publisher.name as "publisher", 0 as "vote"
            from demo
            left join version  on version.id = demo.version
            left join publisher on publisher.id = demo.author
            where demo.name ilike {filter}
            and demo.author = {userid}
            order by %d %s
            limit {pageSize} offset {offset}
          """.format(scala.math.abs(orderBy), mode)
        ).on(
          'pageSize -> pageSize,
          'offset -> offset,
          'filter -> filter,
          'userid -> userId
        ).as(Demo.simpleView *)       //Vote is set to 0 as it won't be used, to avoid a join

        val totalRows = SQL(
          """
            select count(*) from demo
            where name like {filter}
            and author = {userid}
          """
        ).on(
          'filter -> filter,
          'userid -> userId
        ).as(scalar[Long].single)

        Page(demos, page, offset, totalRows)
    }

  }

}
