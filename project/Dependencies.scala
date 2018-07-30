import sbt._

object Dependencies {

  lazy val versions = new {
    val akka = "2.5.9"
    val aws = "1.11.225"
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
    "com.amazonaws" % "aws-java-sdk-cloudwatch" % versions.aws
  ) ++
    loggingDependencies ++
    diDependencies ++
    testDependencies ++
    akkaDependencies
}
