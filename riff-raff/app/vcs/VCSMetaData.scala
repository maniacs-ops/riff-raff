package vcs

import ci.TeamCityBuilds
import ci.teamcity.{Revision, BuildDetail}
import ci.teamcity.TeamCity.BuildLocator
import controllers.Logging

import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.duration._
import scala.util.Try

object VCSMetaData extends Logging {
  def forBuild(projectName: String, buildId: String)(implicit ec: ExecutionContext): Future[Map[String, String]] = {
    val empty = Map.empty[String, String]

    val build = TeamCityBuilds.builds.find { build =>
      build.jobName == projectName && build.number == buildId
    }
    build.map { build =>
      val branch = Map("branch" -> build.branchName)
      val futureMap = for {
        detailedBuild <- BuildDetail(BuildLocator(id=Some(build.id)))
        metaData <- futureOption(detailedBuild.revision.map(forRevision))
        pullRequest <- futureOption(
          for {
            md <- metaData
            vcs <- VCSInfo(md)
          } yield GitHubAPI.pullRequestFor(vcs.repo, vcs.revision)
        )
      } yield {
          branch ++ metaData.getOrElse(empty) ++
            pullRequest.map(_.foldLeft(empty)(_ ++ _.toMap)).getOrElse(empty)
      }
      futureMap.recover {
        case e => {
          log.error("Problem retrieving VCS details", e)
          empty
        }
      }
    }.getOrElse(Future.successful(empty))
  }

  def forRevision(revision: Revision)(implicit ec: ExecutionContext): Future[Map[String, String]] =
    for {
      vcsDetails <- revision.vcsDetails
    } yield
      Map(
        VCSInfo.REVISION -> revision.version,
        VCSInfo.CIURL -> vcsDetails.properties("url")
      )

  def futureOption[T](of: Option[Future[T]])(implicit ec: ExecutionContext): Future[Option[T]] =
    of.fold(Future.successful(Option.empty[T]))(f => f.map(Some(_)))
}
