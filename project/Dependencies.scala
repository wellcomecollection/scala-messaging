import sbt._

object WellcomeDependencies {
  private lazy val versions = new {
    val fixtures = "1.0.0"
    val json = "1.1.1"
    val monitoring = "2.2.0"
    val storage    = "4.4.0"
    val typesafe = "1.0.0"
  }

  val fixturesLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" % "fixtures_2.12" % versions.fixtures,
    "uk.ac.wellcome" % "fixtures_2.12" % versions.fixtures % "test" classifier "tests"
  )

  val jsonLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" % "json_2.12" % versions.json,
    "uk.ac.wellcome" % "json_2.12" % versions.json % "test" classifier "tests"
  )

  val monitoringLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% "monitoring" % versions.monitoring,
    "uk.ac.wellcome" %% "monitoring" % versions.monitoring % "test" classifier "tests"
  )

  val monitoringTypesafeLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% "monitoring_typesafe" % versions.monitoring,
    "uk.ac.wellcome" %% "monitoring_typesafe" % versions.monitoring % "test" classifier "tests"
  )

  val storageLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" %% "storage" % versions.storage,
    "uk.ac.wellcome" %% "storage" % versions.storage % "test" classifier "tests"
  )

  val storageTypesafeLibrary: Seq[ModuleID] = Seq[ModuleID](
    "uk.ac.wellcome" % "storage_typesafe_2.12" % versions.storage,
    "uk.ac.wellcome" % "storage_typesafe_2.12" % versions.storage % "test" classifier "tests"
  )

  val typesafeLibrary: Seq[ModuleID] = Seq[ModuleID](
    "uk.ac.wellcome" % "typesafe-app_2.12" % versions.typesafe,
    "uk.ac.wellcome" % "typesafe-app_2.12" % versions.typesafe % "test" classifier "tests"
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
    val logback = "1.2.3"
  }

  val logbackDependencies = Seq(
    "ch.qos.logback" % "logback-classic" % versions.logback
  )

  val akkaDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % versions.akka,
    "com.typesafe.akka" %% "akka-stream" % versions.akka
  )

  val circeDependencies = Seq(
    "io.circe" %% "circe-core" % versions.circe,
    "io.circe" %% "circe-parser" % versions.circe
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % versions.scalatest % Test,
    "org.mockito" % "mockito-core" % versions.mockito % Test
  )

  val libraryDependencies: Seq[ModuleID] = Seq(
    "com.amazonaws" % "aws-java-sdk-sns" % versions.aws,
    "com.amazonaws" % "aws-java-sdk-sqs" % versions.aws,
    "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % versions.akkaStreamAlpakka,
    "io.circe" %% "circe-yaml" % versions.circeYaml
  ) ++ WellcomeDependencies.jsonLibrary ++
    WellcomeDependencies.monitoringLibrary ++
    WellcomeDependencies.storageLibrary ++
    WellcomeDependencies.fixturesLibrary ++
    WellcomeDependencies.typesafeLibrary ++
    akkaDependencies ++
    circeDependencies ++
    testDependencies ++
    logbackDependencies

  val typesafeDependencies =
    WellcomeDependencies.monitoringTypesafeLibrary ++
      WellcomeDependencies.storageTypesafeLibrary
}
