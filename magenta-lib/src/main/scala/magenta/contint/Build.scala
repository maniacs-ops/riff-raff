package magenta.contint

import java.net.URI
import dispatch.classic._
import scala.util.Try

case class Build(projectName: String, id: String, label: String)

trait ArtifactLocator {
  def location(build: Build): URI
  def escape(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replaceAll("""\+""", "%20")
}

class CompositeArtifactLocator(locators: Seq[ArtifactLocator]) extends ArtifactLocator {
  def location(build: Build) = (locators map (locator => locator.location(build)) filter { uri: URI =>
    (Try {
      Http(url(uri.toString).HEAD >|)
      true
    } recover {
      case e => {
        false
      }
    }).get
  }).head
}

class TeamCityLocator(serverHost: String) extends ArtifactLocator {
  def location(build: Build) =
    new URI(s"$serverHost/guestAuth/repository/download/${escape(build.projectName)}/${build.id}/artifacts.zip")
}

class JenkinsLocator(serverHost: String) extends ArtifactLocator {
  def location(build: Build) =
    new URI(s"$serverHost/job/${escape(build.projectName)}/${build.id}/artifact/target/artifacts.zip")
}

class TravisCILocator(bucket: String) extends ArtifactLocator {
  def location(build: Build) =
    new URI(s"https://travis-ci-artifact-test.s3.amazonaws.com/artifacts/${build.id}/target/artifacts.zip")
}