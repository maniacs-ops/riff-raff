package magenta


import fixtures._
import fixtures.StubPackageType
import fixtures.StubPerAppAction
import fixtures.StubPerHostAction
import fixtures.StubTask
import json._
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import java.io.File
import scala.Some
import tasks.{Task, CopyFile}


class ResolverTest extends FlatSpec with ShouldMatchers {

  val CODE = Stage("CODE")

  val simpleExample = """
  {
    "packages":{
      "htmlapp":{ "type":"file", "apps":["apache"]  }
    },
    "recipes":{
      "all":{
        "default":true,
        "depends":["index-build-only","api-only"]
      },
      "htmlapp-only":{
        "actions":["htmlapp.deploy"],
      }
    }
  }
"""

  "resolver" should "parse json into actions that can be executed" in {
    val parsed = JsonReader.parse(simpleExample, new File("/tmp"))
    val deployRecipe = parsed.recipes("htmlapp-only")

    val host = Host("host1", stage = CODE).app("apache")
    val deployinfo = DeployInfo(host :: Nil)

    val tasks = Resolver.resolve(project(deployRecipe), deployinfo, parameters(deployRecipe))

    tasks.size should be (1)
    tasks should be (List(
      CopyFile(host, "/tmp/packages/htmlapp", "/")
    ))
  }

  val app1 = App("the_role")
  val app2 = App("the_2nd_role")

  val basePackageType = stubPackageType(Seq("init_action_one"), Seq("action_one"), Set(app1))
  val doubleAppPackageType = stubPackageType(
    Seq("init_action_one"), Seq("action_two"), Set(app1, app2))
  val appTwoPackageType = stubPackageType(Seq(), Seq("action_three"), Set(app2))

  val baseRecipe = Recipe("one",
    actionsBeforeApp = basePackageType.mkAction("init_action_one") :: Nil,
    actionsPerHost = basePackageType.mkAction("action_one") :: Nil,
    dependsOn = Nil)

  val multiRolePackageType = stubPackageType(Seq("init_action_one"),Seq("action_one"), Set(app1))

  val multiRoleRecipe = Recipe("two",
    actionsBeforeApp = doubleAppPackageType.mkAction("init_action_one") :: Nil,
    actionsPerHost = basePackageType.mkAction("action_one") ::
      doubleAppPackageType.mkAction("action_two") ::
      appTwoPackageType.mkAction("action_three") :: Nil,
    dependsOn = Nil)

  val host = Host("the_host", stage = CODE).app(app1)
  val deployinfoSingleHost = DeployInfo(List(host))

  val host1 = Host("host1", stage = CODE).app(app1)
  val host2 = Host("host2", stage = CODE).app(app1)
  val host2WithApp2 = Host("host2", stage = CODE).app(app2)

  val deployinfoTwoHosts =
    DeployInfo(List(host1, host2))

  val deployInfoMultiHost =
    DeployInfo(List(host1, host2WithApp2))


  it should "generate the tasks from the actions supplied" in {
    Resolver.resolve(project(baseRecipe), deployinfoSingleHost, parameters(baseRecipe)) should be (List(
      StubTask("init_action_one per app task"),
      StubTask("action_one per host task on the_host", Some(host))
    ))
  }

  it should "only generate tasks for hosts that have apps" in {
    Resolver.resolve(project(baseRecipe),
      DeployInfo(Host("other_host").app("other_app") :: deployinfoSingleHost.hosts), parameters(baseRecipe)) should be (List(
        StubTask("init_action_one per app task"),
        StubTask("action_one per host task on the_host", Some(host))
    ))
  }

  it should "generate tasks for all hosts with app" in {
    Resolver.resolve(project(baseRecipe), deployinfoTwoHosts, parameters(baseRecipe)) should be (List(
      StubTask("init_action_one per app task"),
      StubTask("action_one per host task on host1", Some(host1)),
      StubTask("action_one per host task on host2", Some(host2))
    ))
  }

  it should "generate tasks only for hosts with app per action" in {
    Resolver.resolve(project(multiRoleRecipe), deployInfoMultiHost, parameters(multiRoleRecipe)) should be (List(
      StubTask("init_action_one per app task"),
      StubTask("action_one per host task on host1", Some(host1)),
      StubTask("action_two per host task on host1", Some(host1)),
      StubTask("action_two per host task on host2", Some(host2WithApp2)),
      StubTask("action_three per host task on host2", Some(host2WithApp2))
    ))
  }

  it should "resolve all actions for a given host before moving on to the next host" in {
    val recipe = Recipe("multi-action",
      actionsBeforeApp = StubPerAppAction("init_action_one", Set(app1)) :: Nil,
      actionsPerHost =
        StubPerHostAction("action_one", Set(app1)) ::
        StubPerHostAction("action_two", Set(app1)) ::
        Nil
    )

    Resolver.resolve(project(recipe), deployinfoTwoHosts, parameters(recipe)) should be (List(
      StubTask("init_action_one per app task"),
      StubTask("action_one per host task on host1", Some(host1)),
      StubTask("action_two per host task on host1", Some(host1)),
      StubTask("action_one per host task on host2", Some(host2)),
      StubTask("action_two per host task on host2", Some(host2))
    ))
  }

  it should "prepare dependsOn actions correctly" in {
    val prereq = Recipe("prereq",
      actionsBeforeApp = StubPerAppAction("init_action_one", Set(app1)) :: Nil,
      actionsPerHost = StubPerHostAction("prereq_action", Set(app1)) :: Nil)

    val mainRecipe = Recipe("main",
      actionsBeforeApp = StubPerAppAction("main_init_action", Set(app1)) :: Nil,
      actionsPerHost = StubPerHostAction("main_action", Set(app1)) :: Nil,
      dependsOn = List("prereq"))

    Resolver.resolve(project(mainRecipe, prereq), deployinfoSingleHost, parameters(mainRecipe)) should be (List(
      StubTask("init_action_one per app task"),
      StubTask("prereq_action per host task on the_host", Some(host)),
      StubTask("main_init_action per app task"),
      StubTask("main_action per host task on the_host", Some(host))
    ))

  }
  
  it should "provide a list of all apps for a recipe" in  {
    val recipe = Recipe("recipe", actionsPerHost = StubPerHostAction("action", Set(app1)) :: Nil)
    
    Resolver.possibleApps(project(recipe), recipe.name) should be (app1.name)
  }

  it should "throw an exception if no hosts found and actions require some" in {
    intercept[NoHostsFoundException] {
      Resolver.resolve(project(baseRecipe), DeployInfo(List()), parameters(baseRecipe))
    }
  }

  it should "not throw an exception if no hosts found and only whole app recipes" in {
    val nonHostRecipe = Recipe("nonHostRecipe",
      actionsBeforeApp = StubPerAppAction("init_action_one", Set(app1)) :: Nil,
      dependsOn = Nil)

    Resolver.resolve(project(nonHostRecipe), DeployInfo(List()), parameters(nonHostRecipe))
  }

  it should "only resolve tasks on hosts in the correct stage" in {
    Resolver.resolve(
      project(baseRecipe),
      DeployInfo(List(host, Host("host_in_other_stage", Set(app1), Stage("other_stage")))),
      parameters(baseRecipe)
    ) should be (List(
      StubTask("init_action_one per app task"),
      StubTask("action_one per host task on the_host", Some(host))
    ))
  }


  def project(recipes: Recipe*) = Project(Map.empty, recipes.map(r => r.name -> r).toMap)

  def parameters(recipe: Recipe) =
    DeployParameters(Deployer("Tester"), Build("project", "build"), CODE, RecipeName(recipe.name))
}