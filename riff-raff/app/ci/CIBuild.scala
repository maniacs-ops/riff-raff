package ci

import java.util.concurrent.Executors

import conf.Configuration
import rx.lang.scala.Observable
import org.joda.time.DateTime
import controllers.Logging

import scala.concurrent.{ExecutionContext, Future}

trait CIBuild {
  def jobName: String
  def jobId: String
  def branchName: String
  def number: String
  def id: Long
  def startTime: DateTime
}

trait Job {
  def id: String
  def name: String
}

object CIBuild extends Logging {
  import concurrent.duration._
  import play.api.libs.concurrent.Execution.Implicits._

  implicit val ord = Ordering.by[CIBuild, Long](_.id)

  val pollingPeriod = Configuration.build.pollingPeriodSeconds.seconds

  lazy val jobs: Observable[Job] = builds.map(b => S3Project(b.jobId, b.jobName)).distinct.publish.refCount

  lazy val newBuilds: Observable[CIBuild] = {
    val createObservable = () => Every(pollingPeriod)(InvincibleObservable(() => Observable.from(S3Build.buildJsons))).distinct
    (for {
      location <- InvincibleObservable(createObservable) if !initialFiles.contains(location)
      _ = log.debug(s"Found a new build location: $location")
      build <- Observable.from(S3Build.buildAt(location))
      _ = log.debug(s"Found a new build: $build")
    } yield build).publish.refCount
  }

  lazy val initialFiles: Seq[S3Location] = for {
    location <- S3Build.buildJsons
  } yield location

  lazy val initialBuilds: Future[Seq[CIBuild]] = {
    implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(50))
    log.logger.info(s"Found ${initialFiles.length} builds to parse")
    (Future.traverse(initialFiles) { location =>
      Future(S3Build.buildAt(location))
    }).map(_.flatten)
  }

  lazy val builds: Observable[CIBuild] = Observable.from(initialBuilds).flatMap(Observable.from(_)).merge(newBuilds)
}
