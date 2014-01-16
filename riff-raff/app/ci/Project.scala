package ci

import ci.teamcity.BuildType
import org.joda.time.format.DateTimeFormat
import play.api.libs.ws.WS
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait Project {
  def name: String
  def builds: Seq[Build]
}

case class Build(id: String, label: String)

object Projects {
  def withName(name: String): Option[Project] = all.find(_.name == name)

  val ciProviders = Seq(TeamCityCI, JenkinsCI)

  def all(): Seq[Project] = {
    ciProviders.flatMap(_.listProjects)
  }
}

trait ContinuousIntegration {
  def listProjects: Seq[Project]
}

object TeamCityCI extends ContinuousIntegration {
  val buildFormat = DateTimeFormat.forPattern("HH:mm d/M/yy")

  def listProjects = (TeamCityBuilds.buildTypes map { buildType: BuildType =>
    new Project {
      val name = buildType.fullName
      def builds = TeamCityBuilds.successfulBuilds(name).map(b =>
        Build(b.number.toString, s"${b.number} [${b.branchName}] (${buildFormat.print(b.startDate)})"))
    }
  }).toSeq
}

object JenkinsCI extends ContinuousIntegration {
  import play.api.libs.concurrent.Execution.Implicits._

  def listProjects = {
    val jobsF = WS.url("http://localhost:8080/api/json").get map { r =>
      (r.json \ "jobs" \\ "name") map (_.as[String])
    }
    Await.result(for {
      jobs <-  jobsF
      builds <- Future.traverse(jobs)(job => {
        val escapedJob = job.replaceAll(" ", "%20")
        WS.url(s"http://localhost:8080/job/$escapedJob/api/json").get map { r =>
          job -> ((r.json \ "builds" \\ "number") map (_.as[Int]))
        }
      })
    } yield {
      builds map {
        case (job, buildNumbers) => new Project {
          val name = job
          val builds = buildNumbers map (b => Build(b.toString, b.toString))
        }
      }
    }, 10.seconds)
  }
}