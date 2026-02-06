import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.5.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                   %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "org.typelevel"                 %% "cats-core"                  % "2.13.0",
    "tools.jackson.module"          %% "jackson-module-scala"       % "3.0.4"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"      % bootstrapVersion,
  ).map(_ % Test)

}
