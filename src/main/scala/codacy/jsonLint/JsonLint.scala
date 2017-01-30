package codacy.jsonLint

import codacy.docker.api.Pattern.Definition
import codacy.docker.api.Source.{Directory, File}
import codacy.docker.api.Tool.Specification
import codacy.docker.api._

import scala.util.Try

object JsonLint extends Tool {

  override def apply(source: Directory, configuration: Option[List[Definition]], files: Option[Set[File]])(implicit specification: Specification): Try[List[Result]] = ???
}

