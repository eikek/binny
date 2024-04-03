import sbt._

object Dependencies {

  val fs2Version = "3.10.1"
  val h2Version = "2.2.224"
  val munitVersion = "0.7.29"
  val munitCatsEffectVersion = "1.0.7"
  val mariaDbVersion = "3.3.3"
  val postgresVersion = "42.7.3"
  val slf4jVersion = "2.0.12"
  val tikaVersion = "2.9.2"
  val icu4jVersion = "69.1"
  val kindProjectorVersion = "0.10.3"
  val minioVersion = "8.5.9"

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

  val slf4jNOP = Seq(
    "org.slf4j" % "slf4j-nop" % slf4jVersion
  )
}
