import sbt._

object Dependencies {

  val fs2Version = "3.1.2"
  val h2Version = "1.4.200"
  val munitVersion = "0.7.27"
  val munitCatsEffectVersion = "1.0.5"
  val log4sVersion = "1.10.0"
  val logbackVersion = "1.2.5"
  val organizeImportsVersion = "0.5.0"
  val mariaDbVersion = "2.7.4"
  val postgresVersion = "42.2.23"
  val testContainersVersion = "0.39.7"
  val tikaVersion = "2.1.0"
  val icu4jVersion = "69.1"
  val kindProjectorVersion = "0.10.3"
  val minioVersion = "8.3.0"
  val fs2AwsVersion = "4.0.0-RC2"

  val fs2Aws = Seq(
    "io.laserdisc" %% "fs2-aws-s3" % fs2AwsVersion
  )

  val minio = Seq(
    "io.minio" % "minio" % minioVersion
  )

  val kindProjectorPlugin = "org.typelevel" %% "kind-projector" % kindProjectorVersion

  val icu4j = Seq(
    "com.ibm.icu" % "icu4j" % icu4jVersion
  )

  val tikaCore = Seq(
    "org.apache.tika" % "tika-core" % tikaVersion
  )

  val testContainers = Seq(
    "com.dimafeng" %% "testcontainers-scala-munit" % testContainersVersion,
    "com.dimafeng" %% "testcontainers-scala-mariadb" % testContainersVersion,
    "com.dimafeng" %% "testcontainers-scala-postgresql" % testContainersVersion
  )

  val organizeImports = Seq(
    "com.github.liancheng" %% "organize-imports" % "0.5.0"
  )

  val munit = Seq(
    "org.scalameta" %% "munit" % munitVersion,
    "org.scalameta" %% "munit-scalacheck" % munitVersion
  )

  // https://github.com/typelevel/munit-cats-effect
  val munitCatsEffect = Seq(
    "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion
  )

  val fs2 = Seq(
    "co.fs2" %% "fs2-core" % fs2Version
  )

  val fs2io = Seq(
    "co.fs2" %% "fs2-io" % fs2Version
  )

  val loggingApi = Seq(
    "org.log4s" %% "log4s" % log4sVersion
  )

  val logback = Seq(
    "ch.qos.logback" % "logback-classic" % logbackVersion
  )

  val h2 = Seq(
    "com.h2database" % "h2" % h2Version
  )
  val mariadb = Seq(
    "org.mariadb.jdbc" % "mariadb-java-client" % mariaDbVersion
  )
  val postgres = Seq(
    "org.postgresql" % "postgresql" % postgresVersion
  )
  val databases = h2 ++ mariadb ++ postgres
}
