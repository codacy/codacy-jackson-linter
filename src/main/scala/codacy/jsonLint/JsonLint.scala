package codacy.jsonLint

import better.files.File
import codacy.docker.api.Pattern.Definition
import codacy.docker.api.Result.Issue
import codacy.docker.api.Source.Directory
import codacy.docker.api.Tool.Specification
import codacy.docker.api._
import com.fasterxml.jackson.core.{JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.ObjectMapper

import scala.util.{Failure, Try}


private object JsonPattern extends Enumeration {
  lazy val allPatterns: Set[Pattern.Id] = JsonPattern.values.map(JsonPattern.toPatternId)
  val duplicateField = Value("duplicate-keys")
  val invalidJson = Value("parse-error")

  def fromPatternId(Id: Pattern.Id): Option[JsonPattern.Value] = Try {
    this.withName(Id.value)
  }.toOption

  def toPatternId(pattern: JsonPattern.Value): Pattern.Id = Pattern.Id(pattern.toString)
}

object JsonLint extends Tool {

  private lazy val parser: ObjectMapper = {
    val objectMapper = new ObjectMapper()
    objectMapper.enable(JsonParser.Feature.ALLOW_COMMENTS)
    objectMapper.enable(JsonParser.Feature.ALLOW_YAML_COMMENTS)
    objectMapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
    objectMapper.enable(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS)
    objectMapper.enable(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS)
    objectMapper.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS)
    objectMapper.enable(JsonParser.Feature.IGNORE_UNDEFINED)
    objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)

    objectMapper
  }

  override def apply(source: Directory, configuration: Option[List[Definition]], files: Option[Set[Source.File]])
                    (implicit specification: Specification): Try[List[Result]] = {

    val filesList = files.map(_.map(file => File(file.path))).getOrElse(File(source.path).listRecursively.toSet)

    val issuesList = filesList.collect { case file =>
      Try {
        parser.readTree(file.contentAsString)
      } match {
        case Failure(exp: JsonParseException) =>
          parseException(exp, file, configuration)
        case Failure(exp) =>
          Option(Result.FileError(Source.File(file.path.toString), Option(ErrorMessage(exp.getMessage))))
        case _ =>
          None
      }
    }.flatten

    Try(issuesList.toList)
  }

  private def parseException(exp: JsonParseException, file: File, configuration: Option[List[Definition]]): Option[Result] = {
    def createIssue(pattern: JsonPattern.Value, message: String) = {
      filterResult(Result.Issue(Source.File(file.path.toString),
        Result.Message(message),
        Pattern.Id(pattern.toString),
        Source.Line(exp.getLocation.getLineNr)), configuration)
    }

    val duplicate = """^(Duplicate field .*)""".r
    val msgLine = exp.getMessage.split(System.lineSeparator).head

    msgLine match {
      case duplicate(msg) =>
        createIssue(JsonPattern.duplicateField, msg)
      case msg =>
        val message = msg.replaceFirst("\\(not recognized as one since Feature 'ALLOW_COMMENTS' not enabled for parser\\)", "").trim
        createIssue(JsonPattern.invalidJson, message)
    }
  }


  private def filterResult(issue: Result.Issue, configuration: Option[List[Definition]]): Option[Issue] = {
    val enabledPatterns = configuration.fold(JsonPattern.allPatterns)(_.map(_.patternId).to[Set])

    Option(issue).filter(result =>
      enabledPatterns.contains(result.patternId))
  }
}
