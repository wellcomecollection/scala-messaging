import sbt._

object WellcomeDependencies {
  private lazy val versions = new {
    val fixtures = "1.2.0"
    val json = "2.0.1"
    val monitoring = "4.0.0"
    val typesafe = "2.0.0"
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

  val typesafeLibrary: Seq[ModuleID] = Seq[ModuleID](
    "uk.ac.wellcome" % "typesafe-app_2.12" % versions.typesafe,
    "uk.ac.wellcome" % "typesafe-app_2.12" % versions.typesafe % "test" classifier "tests"
  )
}

object Dependencies {
  lazy val versions = new {
    val aws = "2.11.14"
    val akka = "2.6.4"
    val akkaStreamAlpakka = "1.1.2"
    val circeYaml = "0.8.0"
    val scalatest = "3.1.1"
    val logback = "1.2.3"
    val elasticApm = "1.12.0"
  }

  val openTracingDependencies = Seq(
    "io.opentracing.contrib" %% "opentracing-scala-concurrent" % "0.0.6",
    "io.opentracing" % "opentracing-mock" % "0.33.0" % Test
  )

  val elasticApmBridgeDependencies = Seq (
    "co.elastic.apm" % "apm-opentracing" % versions.elasticApm,
    "co.elastic.apm" % "apm-agent-attach" % versions.elasticApm
  )

  val logbackDependencies = Seq(
    "ch.qos.logback" % "logback-classic" % versions.logback
  )

  val akkaDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % versions.akka,
    "com.typesafe.akka" %% "akka-stream" % versions.akka
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % versions.scalatest % Test
  )

  val libraryDependencies: Seq[ModuleID] = Seq(
    "software.amazon.awssdk" % "sns" % versions.aws,
    "software.amazon.awssdk" % "sqs" % versions.aws,
    "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % versions.akkaStreamAlpakka
      // This needs to be excluded bacuse it conflicts with aws http client "netty-nio-client"
      // and it also causes weird leaks between tests
      exclude("com.github.matsluni", "aws-spi-akka-http_2.12"),
    "io.circe" %% "circe-yaml" % versions.circeYaml
  ) ++ WellcomeDependencies.jsonLibrary ++
    WellcomeDependencies.monitoringLibrary ++
    WellcomeDependencies.fixturesLibrary ++
    WellcomeDependencies.typesafeLibrary ++
    akkaDependencies ++
    testDependencies ++
    logbackDependencies ++
    openTracingDependencies ++
    elasticApmBridgeDependencies

  val typesafeDependencies: Seq[ModuleID] =
    WellcomeDependencies.monitoringTypesafeLibrary
}
