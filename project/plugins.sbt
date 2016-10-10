resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

// keep in sync with the play version in Dependencies
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.0")

addSbtPlugin("com.gu" % "sbt-teamcity-test-reporting-plugin" % "1.5")
addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "0.9.4")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.12")

addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.0.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")