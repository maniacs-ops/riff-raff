import Dependencies._
import Helpers._

val commonSettings = Seq(
  organization := "com.gu",
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-deprecation", "-feature", "-language:postfixOps,reflectiveCalls,implicitConversions", ""),
  version := "1.0",
  resolvers ++= Seq(
    "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
    "Guardian Github Releases" at "http://guardian.github.com/maven/repo-releases"
  )
)

lazy val root = project.in(file("."))
  .aggregate(lib, riffRaffServer)

lazy val lib = project.in(file("magenta-lib"))
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= magentaLibDeps,

    resourceDirectory in Compile := baseDirectory.value / "docs",

    testOptions in Test += Tests.Argument("-oF")
  ))

lazy val riffRaffServer = project.in(file("riff-raff"))
  .dependsOn(lib, riffRaffSharedJvm)
  .enablePlugins(PlayScala, BuildInfoPlugin, RiffRaffArtifact)
  .settings(commonSettings)
  .settings(Seq(
    name := "riff-raff",
    TwirlKeys.templateImports ++= Seq(
      "magenta._",
      "deployment._",
      "controllers._",
      "views.html.helper.magenta._",
      "com.gu.googleauth.AuthenticatedRequest"
    ),

    buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion,
      sbtbuildinfo.BuildInfoKey.constant("gitCommitId", System.getProperty("build.vcs.number", "DEV").dequote.trim),
      sbtbuildinfo.BuildInfoKey.constant("buildNumber", System.getProperty("build.number", "DEV").dequote.trim)
    ),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoPackage := "riffraff",

    resolvers += "Brian Clapper Bintray" at "http://dl.bintray.com/bmc/maven",
    libraryDependencies ++= riffRaffServerDeps,

    packageName in Universal := normalizedName.value,
    topLevelDirectory in Universal := Some(normalizedName.value),
    riffRaffPackageType := (packageZipTarball in Universal).value,

    ivyXML := {
      <dependencies>
        <exclude org="commons-logging"><!-- Conflicts with acl-over-slf4j in Play. --> </exclude>
        <exclude org="oauth.signpost"><!-- Conflicts with play-googleauth--></exclude>
        <exclude org="org.springframework"><!-- Because I don't like it. --></exclude>
        <exclude org="xpp3"></exclude>
      </dependencies>
    },

    fork in Test := false,

    includeFilter in (Assets, LessKeys.less) := "*.less",

    scalaJSProjects := Seq(riffRaffClient),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    pipelineStages := Seq(digest, gzip),
    compile in Compile <<= (compile in Compile) dependsOn scalaJSPipeline
  ))

lazy val riffRaffClient = project.in(file("riff-raff-client"))
  .settings(commonSettings:_*)
  .settings(
    persistLauncher := true,
    persistLauncher in Test := false,
    libraryDependencies ++= riffRaffClientDeps.value,
    jsDependencies ++= jsDeps
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
  .dependsOn(riffRaffSharedJs)

// a special crossProject for configuring a JS/JVM/shared structure
lazy val shared = (crossProject.crossType(CrossType.Pure) in file("riff-raff-shared"))
  .settings(commonSettings:_*)
  .settings(
    libraryDependencies ++= sharedRiffRaffDeps.value
  )
  // set up settings specific to the JS project
  .jsConfigure(_ enablePlugins ScalaJSWeb)

lazy val riffRaffSharedJvm = shared.jvm.settings(name := "riff-raff-shared-jvm")

lazy val riffRaffSharedJs = shared.js.settings(name := "riff-raff-shared-js")
