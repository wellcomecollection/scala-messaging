import sbt._

object WellcomeDependencies {
  private lazy val versions = new {
    val json       = "1.1.0"
    val monitoring = "1.1.0"
    val storage    = "2.7.0"
  }

  val jsonLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" % "json_2.12" % versions.json,
    "uk.ac.wellcome" % "json_2.12" % versions.json % "test" classifier "tests"
  )

  val monitoringLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% "monitoring" % versions.monitoring,
    "uk.ac.wellcome" %% "monitoring" % versions.monitoring % "test" classifier "tests"
  )

  val storageLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% "storage" % versions.storage,
    "uk.ac.wellcome" %% "storage" % versions.storage % "test" classifier "tests"
  )
}

object Dependencies {
  lazy val versions = new {
    val aws = "1.11.225"
    val akka = "2.5.9"
    val akkaStreamAlpakka = "0.20"
    val circe = "0.9.0"
    val circeYaml = "0.8.0"
    val mockito = "1.9.5"
    val scalatest = "3.0.1"
  }

  val akkaDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % versions.akka,
    "com.typesafe.akka" %% "akka-stream" % versions.akka
  )

  val circeDependencies = Seq(
    "io.circe" %% "circe-core" % versions.circe,
    "io.circe" %% "circe-parser"% versions.circe,
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % versions.scalatest % Test,
    "org.mockito" % "mockito-core" % versions.mockito % Test,
  )

  val libraryDependencies = Seq(
    "com.amazonaws" % "aws-java-sdk-sns" % versions.aws,
    "com.amazonaws" % "aws-java-sdk-sqs" % versions.aws,
    "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % versions.akkaStreamAlpakka,
    "io.circe" %% "circe-yaml" % versions.circeYaml
  ) ++ WellcomeDependencies.jsonLibrary ++
    WellcomeDependencies.monitoringLibrary ++
    WellcomeDependencies.storageLibrary ++
    akkaDependencies ++
    circeDependencies ++
    testDependencies
}
