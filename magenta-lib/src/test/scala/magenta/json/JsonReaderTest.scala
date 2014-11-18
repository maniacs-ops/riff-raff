package magenta
package json

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FlatSpec
import java.io.File
import org.json4s.JsonDSL._
import org.json4s._

class JsonReaderTest extends FlatSpec with ShouldMatchers {
  val deployJsonExample = """
  {
    "stack":"content-api",
    "packages":{
      "index-builder":{
        "type":"jetty-webapp",
        "apps":["index-builder"],
      },
      "api":{
        "type":"jetty-webapp",
        "apps":["api"],
        "data": {
          "healthcheck_paths": [
            "/api/index.json",
            "/api/search.json"
          ]
        }
      },
      "solr":{
        "type":"jetty-webapp",
        "apps":["solr"],
        "data": {
          "port": "8400"
        }
      }
    },
    "recipes":{
      "all":{
        "default":true,
        "depends":["index-build-only","api-only"]
      },
      "index-build-only":{
        "default":false,
        "actions":["index-builder.deploy"],
      },
      "api-only":{
        "default":false,
        "actions":[
          "api.deploy","solr.deploy"
        ],
      }
      "api-counter-only":{
        "default":false,
        "actionsPerHost":[
          "api.deploy","solr.deploy"
        ],
        "actionsBeforeApp":[
          "api.uploadArtifacts","solr.uploadArtifacts"
        ],
      }
    }
  }
                          """

  "json parser" should "parse json and resolve links" in {
    val parsed = JsonReader.parse(deployJsonExample, new File("/tmp/abc"))

    parsed.applications should be (Set(App("index-builder"), App("api"), App("solr")))

    parsed.packages.size should be (3)
    parsed.packages("index-builder") should be (DeploymentPackage("index-builder", Seq(App("index-builder")), Map.empty, "jetty-webapp", new File("/tmp/abc/packages/index-builder")))
    parsed.packages("api") should be (DeploymentPackage("api", Seq(App("api")), Map("healthcheck_paths" -> JArray(List("/api/index.json","/api/search.json"))), "jetty-webapp", new File("/tmp/abc/packages/api")))
    parsed.packages("solr") should be (DeploymentPackage("solr", Seq(App("solr")), Map("port" -> "8400"), "jetty-webapp", new File("/tmp/abc/packages/solr")))

    val recipes = parsed.recipes
    recipes.size should be (4)
    recipes("all") should be (Recipe("all", Nil, Nil, List("index-build-only", "api-only")))

    val apiCounterRecipe = recipes("api-counter-only")

    apiCounterRecipe.actionsPerHost.toSeq.length should be (2)
    apiCounterRecipe.actionsBeforeApp.toSeq.length should be (2)
  }

  val minimalExample = """
{
  "packages": {
    "dinky": { "type": "jetty-webapp" }
  }
}
"""

  "json parser" should "infer a single app if none specified" in {
    val parsed = JsonReader.parse(minimalExample, new File("/tmp/abc"))

    parsed.applications should be (Set(App("dinky")))
  }

  val twoPackageExample = """
{
  "packages": {
    "one": { "type": "jetty-webapp" },
    "two": { "type": "jetty-webapp" }
  }
}
"""

  "json parser" should "infer a default recipe that deploys all packages" in {
    val parsed = JsonReader.parse(twoPackageExample, new File("/tmp/abc"))

    val recipes = parsed.recipes
    recipes.size should be(1)
    recipes("default") should be (Recipe("default", actionsPerHost = parsed.packages.values.map(_.mkAction("deploy"))))
  }

  "json parser" should "default to using the package name for the file name" in {
    val parsed = JsonReader.parse(minimalExample, new File("/tmp/abc"))

    parsed.packages("dinky").srcDir should be(new File("/tmp/abc", "/packages/dinky"))
  }

  val withExplicitFileName = """
{
  "packages": {
    "dinky": {
      "type": "jetty-webapp",
      "fileName": "awkward"
    }
  }
}
"""

  "json parser" should "use override file name if specified" in {
    val parsed = JsonReader.parse(withExplicitFileName, new File("/tmp/abc"))

    parsed.packages("dinky").srcDir should be(new File("/tmp/abc", "/packages/awkward"))
  }
}