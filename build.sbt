import com.typesafe.sbt.SbtGit.GitKeys._

val scala212     = "2.12.14"
val scala213     = "2.13.6"
val scala3       = "3.0.1"
val updateReadme = inputKey[Unit]("Update readme")

addCommandAlias("ci", "; lint; +test; readme/updateReadme ;microsite/mdoc; +publishLocal")
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
    (Dependencies.munit ++ Dependencies.munitCatsEffect ++ Dependencies.logback ++ Dependencies.loggingApi)
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
  semanticdbEnabled := true,                        // enable SemanticDB
  semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
  ThisBuild / scalafixDependencies ++= Dependencies.organizeImports
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
      Dependencies.databases.map(_        % Test) ++
        Dependencies.testContainers.map(_ % Test)
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val pg = project
  .in(file("modules/pg"))
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-pg",
    description := "Implementation using PostgreSQLs LargeObject API",
    libraryDependencies ++=
      Dependencies.postgres ++
        Dependencies.testContainers.map(_ % Test)
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val s3 = project
  .in(file("modules/s3"))
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-s3",
    description := "Implementation using the S3 API"
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val tikaDetect = project
  .in(file("modules/tika-detect"))
  .settings(sharedSettings)
  .settings(testSettings)
  .settings(scalafixSettings)
  .settings(
    name := "binny-tika-detect",
    description := "Detect content types using Apache Tika",
    libraryDependencies ++=
      Dependencies.tikaCore ++ Dependencies.icu4j
  )
  .dependsOn(core)

lazy val microsite = project
  .in(file("modules/microsite"))
  .enablePlugins(MicrositesPlugin, MdocPlugin)
  .settings(sharedSettings)
  .settings(noPublish)
  .settings(
    name := "binny-microsite",
    publishArtifact := false,
    publish / skip := true,
    micrositeFooterText := Some(
      s"""
        |<p>&copy; 2020- <a href="https://github.com/eikek/binny">Binny v${latestRelease.value}</a></p>
        |""".stripMargin
    ),
    micrositeName := "Binny",
    micrositeDescription := "Binny – Deal with files",
    micrositeFavicons := Seq(microsites.MicrositeFavicon("favicon.png", "35x35")),
    micrositeBaseUrl := "/binny",
    micrositeAuthor := "eikek",
    micrositeGithubOwner := "eikek",
    micrositeGithubRepo := "binny",
    micrositeGitterChannel := false,
    micrositeShareOnSocial := false,
    run / fork := true,
    scalacOptions := Seq(),
    mdocVariables := Map(
      "VERSION" -> latestRelease.value
    )
  )
  .dependsOn(core % "compile->compile,test", fs, jdbc, pg, s3, tikaDetect)

lazy val readme = project
  .in(file("modules/readme"))
  .enablePlugins(MdocPlugin)
  .settings(sharedSettings)
  .settings(noPublish)
  .settings(
    name := "binny-readme",
    scalacOptions := Seq(),
    mdocVariables := Map(
      "VERSION" -> latestRelease.value
    ),
    updateReadme := {
      mdoc.evaluated
      val out    = mdocOut.value / "readme.md"
      val target = (LocalRootProject / baseDirectory).value / "README.md"
      val logger = streams.value.log
      logger.info(s"Updating readme: $out -> $target")
      IO.copyFile(out, target)
      ()
    }
  )
  .dependsOn(
    core % "compile->compile;compile->test"
  )

val root = project
  .in(file("."))
  .settings(sharedSettings)
  .settings(noPublish)
  .settings(
    name := "binny-root",
    crossScalaVersions := Nil
  )
  .aggregate(core, fs, jdbc, pg, s3, tikaDetect)
