package magenta.teamcity

import java.io.{FileOutputStream, File}
import dispatch.classic._
import magenta.MessageBroker
import dispatch.classic.Request._
import scalax.file.Path
import scalax.file.ImplicitConversions.defaultPath2jfile
import scala.util.Try
import magenta.tasks.CommandLine
import dispatch.classic.StatusCode
import magenta.contint.{ArtifactLocator, Build}

object Artifact {

  def download(artifactLocator: ArtifactLocator, build: Build): File = {
    val dir = Path.createTempDirectory(prefix="riffraff-", suffix="")
    download(artifactLocator, dir, build)
    dir
  }

  def download(artifactLocator: ArtifactLocator, dir: File, build: Build) {
    MessageBroker.info("Downloading artifact")
    val http = new Http {
      override def make_logger = new Logger {
        def info(msg: String, items: Any*) { MessageBroker.verbose("http: " + msg.format(items:_*)) }
        def warn(msg: String, items: Any*) { MessageBroker.info("http: " + msg.format(items:_*)) }
      }
    }

    val artifactUri = artifactLocator.location(build)

    MessageBroker.verbose("Downloading from %s to %s..." format (artifactUri, dir.getAbsolutePath))

    try {
      val artifact = Path.createTempFile(prefix = "riffraff-artifact-", suffix = ".zip")
      http(artifactUri.toString >>> new FileOutputStream(artifact))
      CommandLine("unzip" :: "-q" :: "-d" :: dir.getAbsolutePath :: artifact.getAbsolutePath :: Nil).run()
      artifact.delete()
      MessageBroker.verbose("Extracted files")
    } catch {
      case StatusCode(404, _) =>
        MessageBroker.fail("404 downloading %s\n - have you got the project name and build number correct?" format artifactUri)
    }

    http.shutdown()
  }

  def withDownload[T](artifactLocator: ArtifactLocator, build: Build)(block: File => T): T = {
    val tempDir = Try { download(artifactLocator: ArtifactLocator, build) }
    val result = tempDir.map(block)
    tempDir.map(dir => Path(dir).deleteRecursively(continueOnFailure = true))
    result.get
  }
}
