package vcs

import conf.Configuration
import dispatch._, Defaults._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.{ExecutionContext, Future}

object GitHubAPI {
  lazy val token = Configuration.credentials.lookupSecret("github", "token")

  val root = "https://api.github.com"

  def pullRequestFor(repoIncludingOwner: String, hash: String): Future[Seq[PullRequest]] = {
    for {
      parents <- parentsForRevision(repoIncludingOwner, hash)
      mergedPR <- (Future.traverse(parents)(hash => mergedPullRequestForRevision(hash))).map(_.flatten)
    } yield mergedPR.flatMap {json =>
      for {
        JArray(items) <- json
        JObject(child) <- items
        JField("url", JString(url)) <- child
        JField("title", JString(title)) <- child
      } yield PullRequest(url, title)
    }
  }

  def parentsForRevision(repoIncludingOwner: String, hash: String): Future[Seq[String]] = {
    val req = signedReqFor(s"/repos/$repoIncludingOwner/commits/$hash")
    getJson(req).map(_.toSeq.flatMap(json =>
      for {
        JObject(child) <- json \ "parents" \\ "sha"
        JField("sha", JString(sha)) <- child
      } yield sha
    ))
  }

  def mergedPullRequestForRevision(hash: String): Future[Option[JValue]] = {
    val req = signedReqFor("/search/issues").map(_.addQueryParameter("q", s"$hash is:merged"))
    futureOption(req.map(r => Http(r OK as.json4s.Json)))
  }

  def signedReqFor(endpoint: String) = token.map(OAuth2(_).sign(url(s"$root$endpoint")))
  def getJson(req: Option[Req]): Future[Option[JValue]] =
    futureOption(req.map(r =>
      Http(r > {
        res => res.getStatusCode match {
          case 200 => Some(as.json4s.Json(res))
          case 404 => None
          case sc => throw StatusCode(sc)
        }
      })
    )).map(_.flatten)

  def futureOption[T](of: Option[Future[T]])(implicit ec: ExecutionContext): Future[Option[T]] =
    of.fold(Future.successful(Option.empty[T]))(f => f.map(Some(_)))

}

case class OAuth2(access: String) {
  def sign(req: Req) = req.addHeader("Authorization", s"token $access")
}

case class PullRequest(url: String, title: String) {
  def toMap = Map("pullRequestURL" -> url, "pullRequestTitle" -> title)
}