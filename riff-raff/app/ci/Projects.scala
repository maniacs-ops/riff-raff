package ci

import conf.Configuration
import magenta.contint._
import org.joda.time.format.DateTimeFormat
import java.net.URI
import scala.concurrent.{ExecutionContext, Await, Future}
import play.api.libs.ws.WS
import ci.teamcity.BuildType
import scala.concurrent.duration._
import magenta.contint.Build

object Projects {
  val ciProviders = Configuration.teamcity.serverURL.map(u => new TeamCityCI(u.toString)).toSeq ++
                    Configuration.jenkins.serverURL.map(new JenkinsCI(_))

  def withName(name: String): Option[ContinuousIntegrationProject] = all.find(_.name == name)

  def all(): Seq[ContinuousIntegrationProject] = {
    ciProviders.flatMap(_.listProjects)
  }
}

trait ContinuousIntegrationProject {
  def name: String
  def builds: Seq[Build]
}

trait ContinuousIntegration {
  def listProjects: Seq[ContinuousIntegrationProject]
}

class TeamCityCI(serverHost: String) extends ContinuousIntegration {
  val buildFormat = DateTimeFormat.forPattern("HH:mm d/M/yy")

  def listProjects = (TeamCityBuilds.buildTypes map { buildType: BuildType =>
    new ContinuousIntegrationProject {
      val name = buildType.fullName
      def builds = TeamCityBuilds.successfulBuilds(buildType.project.name) map (bt =>
        Build(name, bt.id.toString, buildType.)) //FIXME
    }
  }).toSeq
}

class JenkinsCI(serverHost: String) extends ContinuousIntegration {

  import ExecutionContext.Implicits.global //FIXME

  def listProjects = {
    val projectsF = WS.url(s"$serverHost/api/json").get map { r =>
      (r.json \ "jobs" \\ "name") map (_.as[String])
    } flatMap { jobs =>
      Future.traverse(jobs)(job => {
        val escapedJob = job.replaceAll(" ", "%20")
        WS.url(s"$serverHost/job/$escapedJob/api/json").get map { r =>
          r.json \ "builds" \\ "number" map (_.as[Int])
        } flatMap { buildNumbers =>
          Future.traverse(buildNumbers)(buildNumber =>
            WS.url(s"$serverHost/job/$escapedJob/$buildNumber/api/json").get map { r =>
              val status = (r.json \ "result").as[String]
              if (status == "SUCCESS") {
                val artifactPath = (r.json \ "artifacts" \\ "relativePath").headOption.map(_.as[String])
                for {
                  path <- artifactPath
                } yield Build(job, buildNumber.toString, buildNumber.toString)
              } else None
            }
          )
        }  map { buildOpts =>
          new ContinuousIntegrationProject {
            val name = job
            val builds = buildOpts.flatten
          }
        }
      })
    }
    Await.result(projectsF, 10.seconds)
  }
}

object RiffRaffArtifactLocator extends ArtifactLocator {
  lazy val locator = new CompositeArtifactLocator(Seq(
    new TeamCityLocator(Configuration.teamcity.serverURL.get.toString),
    new JenkinsLocator(Configuration.jenkins.serverURL.get)
  ))
  def location(build: Build) = locator.location(build)
}