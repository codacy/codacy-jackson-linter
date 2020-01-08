package codacy.jsonLint

import better.files.File
import com.codacy.plugins.api.{ErrorMessage, Options, Source}
import com.codacy.plugins.api.results.{Pattern, Result, Tool}
import com.codacy.plugins.api.results.Pattern.Definition
import com.codacy.plugins.api.results.Result.Issue
import com.fasterxml.jackson.core.{JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.ObjectMapper

import scala.util.{Failure, Try}
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.json.JsonMapper

private object JsonPattern extends Enumeration {
  lazy val allPatterns: Set[Pattern.Id] =
    JsonPattern.values.unsorted.map(JsonPattern.toPatternId)
  val duplicateField = Value("duplicate-keys")
  val invalidJson = Value("parse-error")

  def fromPatternId(Id: Pattern.Id): Option[JsonPattern.Value] =
    Try {
      this.withName(Id.value)
    }.toOption

  def toPatternId(pattern: JsonPattern.Value): Pattern.Id =
    Pattern.Id(pattern.toString)
}

object JsonLint extends Tool {

  private lazy val parser: ObjectMapper = {
    JsonMapper
      .builder()
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
      .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
      .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
      .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
      .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
      .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
      .enable(JsonParser.Feature.IGNORE_UNDEFINED)
      .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
      .build()
  }

  def apply(
      source: Source.Directory,
      configuration: Option[List[Pattern.Definition]],
      files: Option[Set[Source.File]],
      options: Map[Options.Key, Options.Value]
  )(implicit specification: Tool.Specification): Try[List[Result]] = {

    val filesList = files
      .map(_.map(file => File(file.path)))
      .getOrElse(File(source.path).listRecursively.toSet)

    val issuesList = filesList.collect {
      case file =>
        Try {
          parser.readTree(file.toJava)
        } match {
          case Failure(exp: JsonParseException) =>
            parseException(exp, file, configuration)
          case Failure(exp) =>
            Option(
              Result.FileError(
                Source.File(file.path.toString),
                Option(ErrorMessage(exp.getMessage))
              )
            )
          case _ =>
            None
        }
    }.flatten

    Try(issuesList.toList)
  }

  private def parseException(
      exp: JsonParseException,
      file: File,
      configuration: Option[List[Definition]]
  ): Option[Result] = {
    def createIssue(pattern: JsonPattern.Value, message: String) = {
      filterResult(
        Result.Issue(
          Source.File(file.path.toString),
          Result.Message(message),
          Pattern.Id(pattern.toString),
          Source.Line(exp.getLocation.getLineNr)
        ),
        configuration
      )
    }

    val duplicate = """^(Duplicate field .*)""".r
    val msgLine = exp.getMessage.split(System.lineSeparator).head

    msgLine match {
      case duplicate(msg) =>
        createIssue(JsonPattern.duplicateField, msg)
      case msg =>
        val message = msg
          .replaceFirst(
            "\\(not recognized as one since Feature 'ALLOW_COMMENTS' not enabled for parser\\)",
            ""
          )
          .trim
        createIssue(JsonPattern.invalidJson, message)
    }
  }

  private def filterResult(
      issue: Result.Issue,
      configuration: Option[List[Definition]]
  ): Option[Issue] = {
    val enabledPatterns =
      configuration.fold(JsonPattern.allPatterns)(_.map(_.patternId).toSet)

    Option(issue).filter(result => enabledPatterns.contains(result.patternId))
  }
}
