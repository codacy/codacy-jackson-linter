package codacy

import codacy.jsonLint.JsonLint
import com.codacy.tools.scala.seed.DockerEngine

object Engine extends DockerEngine(JsonLint)()
