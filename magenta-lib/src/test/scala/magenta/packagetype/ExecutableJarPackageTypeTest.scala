package magenta.packagetype

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import magenta.{Host, ExecutableJarPackageType, Package}
import java.io.File
import net.liftweb.json.Implicits._
import net.liftweb.json.JsonAST.{JValue, JArray, JString}
import magenta.tasks.{RmTmpdir, ExecuteJar, CopyFile, Mkdir}


class ExecutableJarPackageTypeTest extends FlatSpec with ShouldMatchers {
  val host = Host("host_name")
  val user = "user"
  val name = "test-package"
  val srcDir = new File("/tmp/packages/webapp")

  "Executable jar package type" should "have a run action" in {
    val p = Package(name, Set.empty, Map.empty, ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)
    if (Nil == jar.perHostActions("run")(host)) {
      fail("ExecutableJarPackageType had no tasks for run action")
    }
  }
  it should "provide an appropriate default jar name" in {
    val p = Package(name, Set.empty, Map.empty, ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    jar.jarFilename should equal("%s.jar".format(name))
  }
  it should "allow the the jar name to be specified" in {
    val p = Package(name, Set.empty, Map("jarFilename" -> "test-jarname.jar"), ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    jar.jarFilename should equal("test-jarname.jar")
  }
  it should "allow arguments to be configured for the jar" in {
    val args = List("arg1", "arg2")
    val args_json = JArray(args map {JString(_)})
    val p = Package(name, Set.empty, Map("args" -> args_json), ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    jar.args should equal(args)
  }
  it should "provide a default user" in {
    val p = Package(name, Set.empty, Map.empty, ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    jar.user should equal("jvmuser")
  }
  it should "allow the default user to be overriden" in {
    val p = Package(name, Set.empty, Map("user" -> user), ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    jar.user should equal(user)
  }
  it should "clean the service name before using it as the default jar filename" in {
    val p = Package("test/../../sneaky-name", Set.empty, Map.empty, ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    jar.jarFilename should equal("sneaky-name.jar")
  }
  it should "clean the propvided jar name" in {
    val p = Package(name, Set.empty, Map("jarFilename" -> JString("test/../../sneaky-name.jar")), ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    jar.jarFilename should equal("sneaky-name.jar")
  }
  it should "clean the service name before using it in the execute jar path" in {
    val p = Package("test/../../sneaky-name", Set.empty, Map.empty, ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    jar.perHostActions("run")(host) match {
      case List(_, _, ExecuteJar(_, jarPath, _), _) => {
        jarPath contains ".." should equal(false)
      }
      case _ => fail("run action did not include ExecuteJar task")
    }
  }
  it should "have a run action that executes the jar" in {
    val args = List("arg1", "arg2")
    val jarFilename = "test.jar"
    val data = Map(
      "args" -> JArray(args map {JString(_)}),
      "jarFilename" -> JString(jarFilename),
      "user" -> JString(user))

    val p = Package(name, Set.empty, data, ExecutableJarPackageType.name, srcDir)
    val jar = new ExecutableJarPackageType(p)

    val tasks = jar.perHostActions("run")(host)
    tasks match {
      case List(
      Mkdir(mkdirHost, mkdirPath),
      CopyFile(copyHost, copySource, copyDest),
      ExecuteJar(executeHost, executeJarPath, executeArgs),
      RmTmpdir(rmdirHost, rmdirPath)
      ) => {
        mkdirHost should equal(host as user)
        mkdirPath should startWith("/tmp/execute-jar-")
        mkdirPath should endWith("/")
        copyHost should equal(host as user)
        copySource should endWith("/" + jarFilename)
        copyDest should equal(mkdirPath)
        executeHost should equal(host as user)
        executeJarPath should equal(mkdirPath + jarFilename)
        executeArgs should equal(args)
        rmdirHost should equal(host as user)
        rmdirPath should equal(mkdirPath)
      }
      case _ => fail("run action tasks were not correct, got %s".format((tasks map {_.verbose}) mkString ", "))
    }
  }
}
