import sbt._

object WellcomeDependencies {
  private lazy val versions = new {
    val monitoring = "1.1.0"
    val storage = "1.5.0"
  }

  val monitoringLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" % "monitoring_2.12" % versions.monitoring,
    "uk.ac.wellcome" % "monitoring_2.12" % versions.monitoring % "test" classifier "tests"
  )

  val storageLibrary: Seq[ModuleID] = Seq(
    "uk.ac.wellcome" % "storage_2.12" % versions.storage,
    "uk.ac.wellcome" % "storage_2.12" % versions.storage % "test" classifier "tests"
  )
}

object Dependencies {

  lazy val versions = new {
    val akka = "2.5.9"
    val akkaStreamAlpakkaS3 = "0.17"
    val aws = "1.11.225"
    val circeYaml = "0.8.0"
    val guice = "4.2.0"
    val logback = "1.1.8"
    val mockito = "1.9.5"
    val scalatest = "3.0.1"
  }

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % versions.scalatest % Test,
    "org.mockito" % "mockito-core" % versions.mockito % Test,
    "com.google.inject.extensions" % "guice-testlib" % versions.guice % Test,
    "com.typesafe.akka" %% "akka-actor" % versions.akka % Test,
    "com.typesafe.akka" %% "akka-stream" % versions.akka % Test
  )

  val loggingDependencies = Seq(
    "org.clapper" %% "grizzled-slf4j" % "1.3.2",
    "ch.qos.logback" % "logback-classic" % versions.logback,
    "org.slf4j" % "slf4j-api" % "1.7.25"
  )

  val diDependencies = Seq(
    "com.google.inject" % "guice" % versions.guice
  )

  val akkaDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % versions.akka,
    "com.typesafe.akka" %% "akka-stream" % versions.akka
  )

  val libraryDependencies = Seq(
    "com.amazonaws" % "aws-java-sdk-sns" % versions.aws,
    "com.amazonaws" % "aws-java-sdk-sqs" % versions.aws,
    "com.amazonaws" % "aws-java-sdk-s3" % versions.aws,
    "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % versions.akkaStreamAlpakkaS3,
    "io.circe" %% "circe-yaml" % versions.circeYaml
  ) ++
    WellcomeDependencies.monitoringLibrary ++
    WellcomeDependencies.storageLibrary ++
    loggingDependencies ++
    diDependencies ++
    testDependencies ++
    akkaDependencies
}
