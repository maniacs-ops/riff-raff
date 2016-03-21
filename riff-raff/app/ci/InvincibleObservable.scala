package ci

import controllers.Logging
import rx.lang.scala.Observable

import scala.util.control.NonFatal

object InvincibleObservable extends Logging {

  def apply[T](createObservable: () => Observable[T]): Observable[T] = {
    createObservable().onErrorResumeNext {
      case NonFatal(e) =>
        log.warn("Exception thrown in Observable. Carrying on regardless.", e)
        createObservable()
    }
  }

}
