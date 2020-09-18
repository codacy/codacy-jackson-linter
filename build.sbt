import com.typesafe.sbt.packager.docker.Cmd
import sjsonnew._
import sjsonnew.BasicJsonProtocol._
import sjsonnew.support.scalajson.unsafe._

name := "codacy-jackson-linter"

scalaVersion := "2.13.1"

lazy val toolVersionKey = settingKey[String](
  "The version of the underlying tool retrieved from patterns.json"
)
toolVersionKey := {
  case class Patterns(name: String, version: String)
  implicit val patternsIso: IsoLList[Patterns] =
    LList.isoCurried(
      (p: Patterns) => ("name", p.name) :*: ("version", p.version) :*: LNil
    ) {
      case (_, n) :*: (_, v) :*: LNil => Patterns(n, v)
    }

  val jsonFile = (resourceDirectory in Compile).value / "docs" / "patterns.json"
  val json = Parser.parseFromFile(jsonFile)
  val patterns = json.flatMap(Converter.fromJson[Patterns])
  patterns.get.version
}

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "com.codacy" %% "codacy-engine-scala-seed" % "5.0.1",
  "com.fasterxml.jackson.core" % "jackson-core" % toolVersionKey.value
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
