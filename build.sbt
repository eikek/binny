import com.github.sbt.git.SbtGit.GitKeys._

val scala212 = "2.12.17"
val scala213 = "2.13.15"
val scala3 = "3.6.1"

addCommandAlias("ci", "lint; +test; microsite/mdoc; +publishLocal")
addCommandAlias(
  "lint",
  "; scalafmtSbtCheck; scalafmtCheckAll; Compile/scalafix --check; Test/scalafix --check"
)
addCommandAlias("fix", "; Compile/scalafix; Test/scalafix; scalafmtSbt; scalafmtAll")

val sharedSettings = Seq(
  organization := "com.github.eikek",
  scalaVersion := scala213,
  scalacOptions ++=
    Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-language:higherKinds"
    ) ++
      (if (scalaBinaryVersion.value.startsWith("2.12"))
         List(
           "-Xfatal-warnings", // fail when there are warnings
           "-Xlint",
           "-Yno-adapted-args",
           "-Ywarn-dead-code",
           "-Ywarn-unused",
           "-Ypartial-unification",
           "-Ywarn-value-discard"
         )
       else if (scalaBinaryVersion.value.startsWith("2.13"))
         List("-Werror", "-Wdead-code", "-Wunused", "-Wvalue-discard")
       else if (scalaBinaryVersion.value.startsWith("3"))
         List(
           "-explain",
           "-explain-types",
           "-indent",
           "-print-lines",
           "-Ykind-projector",
           "-Xmigration",
           "-Xfatal-warnings"
         )
       else
         Nil),
  libraryDependencies ++= {
    if (scalaBinaryVersion.value == "3") Nil
    else List(compilerPlugin(Dependencies.kindProjectorPlugin))
  },
  crossScalaVersions := Seq(scala213, scala3),
  Compile / console / scalacOptions := Seq(),
  licenses := Seq("MIT" -> url("http://spdx.org/licenses/MIT")),
  homepage := Some(url("https://github.com/eikek/binny")),
  versionScheme := Some("early-semver")
) ++ publishSettings

lazy val publishSettings = Seq(
  developers := List(
    Developer(
      id = "eikek",
      name = "Eike Kettner",
      url = url("https://github.com/eikek"),
      email = ""
    )
  ),
  Test / publishArtifact := false
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

val testSettings = Seq(
  libraryDependencies ++=
    (Dependencies.munit ++
      Dependencies.munitCatsEffect ++
      Dependencies.fs2io ++
      Dependencies.slf4jNOP)
      .map(_ % Test),
  Test / parallelExecution := false,
  Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "-b")
)

val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    sbtVersion,
    gitHeadCommit,
    gitHeadCommitDate,
    gitUncommittedChanges,
    gitDescribedVersion
  ),
  buildInfoOptions += BuildInfoOption.ToJson,
  buildInfoOptions += BuildInfoOption.BuildTime,
  buildInfoPackage := "binny"
)

val scalafixSettings = Seq(
  semanticdbEnabled := true, // enable SemanticDB
  semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version
)

lazy val core = project
  .in(file("modules/core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(buildInfoSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-core",
    description := "The binny api",
    libraryDependencies ++=
      Dependencies.fs2
  )

lazy val tikaDetect = project
  .in(file("modules/tika-detect"))
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-tika-detect",
    description := "Detect content types using Apache Tika",
    libraryDependencies ++=
      Dependencies.tikaCore
  )
  .dependsOn(core)

lazy val fs = project
  .in(file("modules/fs"))
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-fs",
    description := "An filesystem based implementation using java.nio.file.",
    libraryDependencies ++=
      Dependencies.fs2io
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val jdbc = project
  .in(file("modules/jdbc"))
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-jdbc",
    description := "Implementation backed by a SQL database using pure JDBC",
    libraryDependencies ++=
      Dependencies.databases.map(_ % Test)
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val pglo = project
  .in(file("modules/pglo"))
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-pglo",
    description := "Implementation using PostgreSQLs LargeObject API",
    libraryDependencies ++=
      Dependencies.postgres
  )
  .dependsOn(core % "compile->compile;test->test", jdbc % "compile->compile;test->test")

lazy val minio = project
  .in(file("modules/minio"))
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-minio",
    description := "Implementation using the S3 API using MinIO SDK",
    libraryDependencies ++=
      Dependencies.minio ++
        Dependencies.fs2io
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val microsite = project
  .in(file("modules/microsite"))
  .enablePlugins(MicrositesPlugin, MdocPlugin)
  .settings(sharedSettings)
  .settings(scalafixSettings)
  .settings(noPublish)
  .settings(
    name := "binny-microsite",
    publishArtifact := false,
    publish / skip := true,
    micrositeFooterText := Some(
      s"""
         |<p>&copy; 2021- <a href="https://github.com/eikek/binny">Binny v${latestRelease.value}</a></p>
         |""".stripMargin
    ),
    micrositeName := "Binny",
    micrositeDescription := "Binny – Scala library for files in databases",
    micrositeFavicons := Seq(microsites.MicrositeFavicon("favicon.png", "35x35")),
    micrositeBaseUrl := "/binny",
    micrositeAuthor := "eikek",
    micrositeGithubOwner := "eikek",
    micrositeGithubRepo := "binny",
    micrositeGitterChannel := false,
    micrositeShareOnSocial := false,
    run / fork := true,
    scalacOptions := Seq("-Ywarn-unused"),
    mdocVariables := Map(
      "VERSION" -> latestRelease.value
    ),
    libraryDependencies ++=
      Dependencies.h2
  )
  .dependsOn(
    core % "compile->compile,test",
    fs,
    jdbc % "compile->compile,test",
    pglo,
    minio % "compile->compile,test",
    tikaDetect
  )

val root = project
  .in(file("."))
  .settings(sharedSettings)
  .settings(noPublish)
  .settings(
    name := "binny-root",
    crossScalaVersions := Nil
  )
  .aggregate(core, fs, jdbc, pglo, minio, tikaDetect, microsite)
