package codacy

import codacy.dockerApi.DockerEngine
import codacy.jsonLint.JsonLint

object Engine extends DockerEngine(JsonLint)
