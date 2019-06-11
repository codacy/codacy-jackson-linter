import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := """codacy-engine-jackson-linter"""

version := "1.0-SNAPSHOT"

val languageVersion = "2.12.7"

scalaVersion := languageVersion

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.7.3",
  "com.codacy" %% "codacy-engine-scala-seed" % "3.0.9",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.8.6"
)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

version in Docker := "1.0"

val installAll =
  s"""apk update &&
     |apk add --no-cache bash curl &&
     |rm -rf /var/cache/apk/*""".stripMargin.replaceAll(System.lineSeparator(), " ")

mappings.in(Universal) ++= resourceDirectory
  .in(Compile)
  .map { resourceDir: File =>
    val src = resourceDir / "docs"
    val dest = "/docs"

    (for {
      path <- better.files.File(src.toPath).listRecursively()
      if !path.isDirectory
    } yield path.toJava -> path.toString.replaceFirst(src.toString, dest)).toSeq
  }
  .value

val dockerUser = "docker"
val dockerGroup = "docker"

daemonUser in Docker := dockerUser

daemonGroup in Docker := dockerGroup

dockerBaseImage := "develar/java"

dockerCommands := dockerCommands.value.flatMap {
  case cmd@(Cmd("ADD", _)) => List(
    Cmd("RUN", s"adduser -u 2004 -D $dockerUser"),
    cmd,
    Cmd("RUN", installAll),
    Cmd("RUN", "mv /opt/docker/docs /docs"),
    ExecCmd("RUN", Seq("chown", "-R", s"$dockerUser:$dockerGroup", "/docs"): _*)
  )
  case other => List(other)
}
