ThisBuild / tlBaseVersion := "0.1"
ThisBuild / tlUntaggedAreSnapshots := false
ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("2.12.15", "3.1.0", "2.13.8")

lazy val root = tlCrossRootProject.aggregate(bayou)

lazy val bayou = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("bayou"))
  .settings(
    name := "bayou",
    libraryDependencies += "org.tpolecat" %%% "natchez-core" % "0.1.6"
  )

lazy val test = project
  .in(file("test"))
  .dependsOn(bayou.jvm)
  .enablePlugins(NoPublishPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "3.3.7",
      "co.fs2" %%% "fs2-core" % "3.2.5",
      "org.typelevel" %%% "log4cats-slf4j" % "2.2.0",
      "org.tpolecat" %%% "natchez-log" % "0.1.6"
    )
  )
