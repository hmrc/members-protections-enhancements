import uk.gov.hmrc.DefaultBuildSettings

val appName = "members-protections-enhancements"

inThisBuild(
  List(
    scalaVersion := "3.3.7",
    majorVersion := 0,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalafmtOnCompile := true,
    scalafixOnCompile := true,
    PlayKeys.playDefaultPort := 30030
  )
  .settings(scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-Wconf:msg=unused import&src=conf/.*:s",
    "-Wconf:msg=Flag.*repeatedly:s",
    "-Wconf:src=routes/.*:s")
  )
  .settings(CodeCoverageSettings.settings *)

lazy val it = project
  .in(file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    Test / fork := true,
    Test / scalafmtOnCompile := true,
    Test / unmanagedResourceDirectories += baseDirectory.value / "it" / "test" / "resources"
  )

addCommandAlias("runTestsWithCoverage", "; clean ; coverage ; test ; it/test ; coverageReport ;")
