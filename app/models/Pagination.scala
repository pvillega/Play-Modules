package models

/**
 * Created by IntelliJ IDEA.
 * User: pvillega
 * Date: 19/03/12
 * Time: 12:38
 * Support elements for pagination
 */


/**
 * Defines a Page of elements to be rendered in a template
 * @param items items in the page
 * @param page page number
 * @param offset page offset
 * @param total total elements
 * @tparam A type of element to render
 */
case class Page[+A](items: Seq[A], page: Int, offset: Long, total: Long) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}