import com.typesafe.sbt.packager.docker.Cmd

name := "codacy-jackson-linter"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "com.codacy" %% "codacy-engine-scala-seed" % "3.1.0",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.10.2"
)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

val installAll =
  """apk update &&
     |apk add --no-cache bash curl &&
     |rm -rf /var/cache/apk/*""".stripMargin
    .replaceAll(System.lineSeparator(), " ")

mappings in Universal ++= {
  (resourceDirectory in Compile) map { resourceDir: File =>
    val src = resourceDir / "docs"
    val dest = "/docs"

    for {
      path <- src.allPaths.get if !path.isDirectory
    } yield path -> path.toString.replaceFirst(src.toString, dest)
  }
}.value

val dockerUser = "docker"
val dockerGroup = "docker"

daemonUser in Docker := dockerUser

daemonGroup in Docker := dockerGroup

dockerBaseImage := "openjdk:8-jre-alpine"

dockerCommands := dockerCommands.value.flatMap {
  case cmd @ (Cmd("ADD", _)) =>
    List(
      Cmd("RUN", s"adduser -u 2004 -D $dockerUser"),
      cmd,
      Cmd("RUN", installAll),
      Cmd("RUN", "mv /opt/docker/docs /docs"),
      Cmd(
        "RUN",
        s"chown -R $dockerUser:$dockerGroup /docs"
      )
    )
  case other => List(other)
}
