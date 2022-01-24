ThisBuild / tlBaseVersion := "0.1"

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
