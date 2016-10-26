package riffraff.client

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import org.scalajs.jquery.jQuery

object RiffRaffClient extends JSApp {
  @JSExport
  def main(): Unit = {
    println("Testing that this goes to the console")
    val body = jQuery("body")
    println(body)
    val result = body.append("<p>Hello World!</p>")
    println(s"appended: $result")
  }
}
