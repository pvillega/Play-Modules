package models

import play.api.mvc.{JavascriptLitteral, QueryStringBindable}
import play.api.Logger


/**
 * Created with IntelliJ IDEA.
 * User: pvillega
 * Date: 07/05/12
 * Time: 12:06
 * QueryStringBinders for some data types missing in 2.0.1
 */
//TODO: remove when updating to 2.1
object QueryBinders {

  /**
   * QueryString binder for List
   */
  implicit def bindableList[T: QueryStringBindable] = new QueryStringBindable[List[T]] {
    def bind(key: String, params: Map[String, Seq[String]]) = Some(Right(bindList[T](key, params)))
    def unbind(key: String, values: List[T]) = unbindList(key, values)
  }

  private def bindList[T: QueryStringBindable](key: String, params: Map[String, Seq[String]]): List[T] = {
    if(Logger.isDebugEnabled){
      Logger.debug("QueryBinders.bindList [%s | %s]".format(key, params))
    }
    for {
      listKey <- params.keys.filter(_.startsWith(key)).toList
      values <- params.get(listKey).filterNot(_.isEmpty).toList
      rawValue <- values
      bound <- implicitly[QueryStringBindable[T]].bind(key, Map(key -> Seq(rawValue)))
      value <- bound.right.toOption
    } yield value
  }

  private def unbindList[T: QueryStringBindable](key: String, values: Iterable[T]): String = {
    (for (value <- values) yield {
      implicitly[QueryStringBindable[T]].unbind(key, value)
    }).mkString("&")
  }

  /**
   * Convert a Scala List[T] to Javascript array
   */
  implicit def literalList[T](implicit jsl: JavascriptLitteral[T]) = new JavascriptLitteral[List[T]] {
    def to(value: List[T]) = value match {
      case l: List[_] if !l.isEmpty => "[" + value.map { v => jsl.to(v)+"," } +"]"
      case _ => "[]"
    }
  }

}
