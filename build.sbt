import java.util.UUID
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider
val projectName = "messaging"
val projectVersion = "8.0.0"

enablePlugins(DockerComposePlugin)

// Everything below this line is generic boilerplate that should be reusable,
// unmodified, in all of our Scala libraries that have a "core" and a "typesafe"
// version.

val settings: Seq[Def.Setting[_]] = Seq(
  scalaVersion := "2.12.6",
  organization := "uk.ac.wellcome",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-encoding",
    "UTF-8",
    "-Xlint",
    "-Xverify",
    "-Xfatal-warnings",
    "-feature",
    "-language:postfixOps"
  ),
  parallelExecution in Test := false,
  resolvers ++= Seq(
    "S3 releases" at "s3://releases.mvn-repo.wellcomecollection.org/"
  ),
  publishMavenStyle := true,
  publishTo := Some(
    "S3 releases" at "s3://releases.mvn-repo.wellcomecollection.org/"
  ),
  publishArtifact in Test := true,
  version := projectVersion
)

s3CredentialsProvider := { _ =>
  val builder = new STSAssumeRoleSessionCredentialsProvider.Builder(
    "arn:aws:iam::760097843905:role/platform-read_only",
    UUID.randomUUID().toString
  )

  builder.build()
}

lazy val lib =
  project
    .withId(projectName)
    .in(new File(projectName))
    .settings(settings)
    .settings(libraryDependencies ++= Dependencies.libraryDependencies)

lazy val lib_typesafe =
  project
    .withId(s"${projectName}_typesafe")
    .in(new File(s"${projectName}_typesafe"))
    .settings(settings)
    .dependsOn(lib % "compile->compile;test->test")
    .settings(libraryDependencies ++= Dependencies.typesafeDependencies)

lazy val root = (project in file("."))
  .withId("scala-messaging")
  .aggregate(lib, lib_typesafe)
  .settings(
    Seq(
      // We don't want to publish the aggregate project, just the sub projects.
      // See https://stackoverflow.com/a/46986683/1558022
      skip in publish := true
    ))
