import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.3.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                   %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "org.typelevel"                 %% "cats-core"                  % "2.13.0",
    "com.chuusai"                   %% "shapeless"                  % "2.4.0-M1",
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"       % "2.20.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"      % bootstrapVersion,
  ).map(_ % Test)

}
