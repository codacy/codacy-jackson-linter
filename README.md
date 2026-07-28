# Codacy Jackson Linter (JSON)

This is the docker engine we use at Codacy to have a JSON linter using [Jackson](https://github.com/FasterXML/jackson-core).
You can also create a docker to integrate the tool and language of your choice!
Check the **Docs** section for more information.

## Usage

You can create the docker by doing:

    sbt docker:publishLocal

The docker is ran with the following command:

    docker run -it -v $srcDir:/src  <DOCKER_NAME>:<DOCKER_VERSION>

To update the tool version you need to update `src/main/resources/docs/patterns.json` with
the new version. Sbt will checkout the new version on startup.

## Docs

[Tool Developer Guide](https://support.codacy.com/hc/en-us/articles/207994725-Tool-Developer-Guide)

[Tool Developer Guide - Using Scala](https://support.codacy.com/hc/en-us/articles/207280379-Tool-Developer-Guide-Using-Scala)

## Test

We use the [codacy-plugins-test](https://github.com/codacy/codacy-plugins-test) to test our external tools integration.
You can follow the instructions there to make sure your tool is working as expected.

## Agent Playbook: Updating This Repository End-to-End

This section is written for an AI coding agent (or a human) tasked with updating this repo — most commonly bumping the wrapped Jackson (`jackson-core`) version, but also base image / orb / dependency bumps. Follow it top to bottom; it tells you what to change, how to test locally, and how to interpret CI so you can iterate on failures without guessing.

### 1. What this repository is

This is a small **Codacy-authored engine**, not a wrapper around a well-known third-party linter binary. It is a thin Scala application (`src/main/scala/codacy/Engine.scala`, built on `codacy-engine-scala-seed`) whose actual linting logic (`src/main/scala/codacy/jsonLint/JsonLint.scala`) parses JSON files with the [Jackson](https://github.com/FasterXML/jackson-core) `jackson-core`/`jackson-databind` library and turns any `JsonParseException` it throws into one of two Codacy patterns: `parse-error` (invalid/unparsable JSON) and `duplicate-keys` (Jackson's strict-duplicate-detection catching overlapping keys). There is no real "Jackson Linter" upstream project being wrapped — Jackson is a JSON parsing library being repurposed here as the validation engine, and all Codacy-facing behavior (pattern IDs, categories, messages) is hand-written in `JsonLint.scala`.

The `src/main/resources/docs/` directory is machine/build-consumed, not just documentation:

- `docs/patterns.json` — **the single source of truth for both the pattern list AND the pinned `jackson-core` version** (top-level `"version"` field). It is small and **hand-maintained directly** — there is no `DocGenerator`-style script in this repo that regenerates it from an upstream source.
- `docs/description/description.json` + `docs/description/*.md` — hand-maintained titles/descriptions for the two patterns.
- `docs/tests/*` and `docs/multiple-tests/*` — fixtures used by `codacy-plugins-test`.
- `docs/tool-description.md` — short hand-maintained blurb about the tool.

Build tool: **sbt** (Scala). `build.sbt` reads the version straight out of `docs/patterns.json` at build time via the `toolVersionKey` setting and uses it to pin the `jackson-core` library dependency — so bumping the Jackson version is a one-file change (`patterns.json`), and `build.sbt` picks it up automatically; there is nothing to separately edit in `build.sbt` for a pure version bump.

### 2. Files that encode versions — check all of these on every update

| File | What it controls | What to check |
|---|---|---|
| `src/main/resources/docs/patterns.json` → top-level `"version"` | The pinned `jackson-core` version, read by `build.sbt`'s `toolVersionKey` and injected into `libraryDependencies` | Bump to the target Jackson release. Confirm the version exists on [Maven Central for `com.fasterxml.jackson.core:jackson-core`](https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core). |
| `build.sbt` → `codacy-engine-scala-seed` dependency | Codacy's engine SDK/base library | Check [Maven Central](https://mvnrepository.com/artifact/com.codacy/codacy-engine-scala-seed) if asked to update it; unrelated to Jackson bumps. |
| `build.sbt` → `play-json`, `better-files` dependencies | Auxiliary libraries | Only touch if explicitly asked; not tied to Jackson version. |
| `build.sbt` → `scalaVersion` | Scala compiler version | Only touch if explicitly asked or required by a new dependency. |
| `build.sbt` → `dockerBaseImage` | JRE the packaged app runs on (currently `amazoncorretto:11-alpine3.22-full`) | Only bump if the new Jackson version raises its minimum JDK requirement, or if asked explicitly. |
| `.circleci/config.yml` → `codacy/base` orb | Shared CircleCI steps (checkout, versioning, sbt build/format/test, docker publish, tagging) | Check the latest published orb version; `git log -p .circleci/config.yml` shows prior bump commits as a fallback reference. |
| `.circleci/config.yml` → `codacy/plugins-test` orb | Runs `codacy-plugins-test` in CI after the image is built | Same as above. |
| `project/build.properties` (sbt version) / `project/plugins.sbt` (`codacy-sbt-plugin`) | Build tooling versions | Rarely needs touching on a pure Jackson bump; prior bump commits touched these alongside orb bumps — check if the build fails to load. |

### 3. Step-by-step update procedure

1. **Bump the Jackson version** by editing the `"version"` field in `src/main/resources/docs/patterns.json`. (Bump `.circleci/config.yml` orbs, `build.sbt` deps, `project/build.properties`, or `project/plugins.sbt` too, if the task scope includes those — see the table above and the real prior bump commit `0932910` "TCE-1369 Bump JacksonLinter 2.19.2" for an example of a bump that touched all of them together.)
2. **There is no doc-generation step** — `patterns.json` and `docs/description/*` are hand-maintained; only edit them if the pattern set itself is changing (it normally isn't for a pure version bump).
3. **Compile, format-check, and test:** `sbt "clean; scalafmtCheckAll; Test/scalafmtCheck; scalafmtCheck; test"` (this mirrors exactly what CI's `publish_docker_local` job runs).
4. **Build the Docker image locally:** `sbt "set name := \"codacy-jackson-linter\"; set version in Docker := \"latest\"; docker:publishLocal"`.
5. **Run `codacy-plugins-test` locally** before pushing — clone https://github.com/codacy/codacy-plugins-test and run its multiple-tests/DockerTest commands against your local image tag (CI runs `codacy_plugins_test/run` with `run_multiple_tests: true`, exercising `docs/tests/*` and `docs/multiple-tests/*`).
6. **Iterate on failures locally**, re-running the relevant test command after each fix.
7. **Commit** the version bump together with any other files it required (orb bumps, etc.) in one change.
8. **Push and open a PR.** CI (`.circleci/config.yml`) runs `checkout_and_version` -> `publish_docker_local` (clean/format/test/build+save image) -> `plugins_test` -> `publish_dockerhub` (master only) -> `tag_version` (master only).
9. **Poll the PR's real CI checks until they all pass — local validation is NOT the finish line.** After every push, run `gh pr checks <pr-url>` and keep re-polling (short sleep while any check is `pending`) until all checks finish. If a check fails, fetch its actual log (CircleCI API/UI for the failing job — don't guess), find the true root cause, fix it, push again (never `--no-verify`, never force-push), and re-poll. Repeat until every check is green. The CI environment's toolchain can differ from your local one, so a clean local run does not guarantee CI passes. Only stop iterating when every check passes, or you hit a genuine product/infra decision that needs a human — in which case explain it in the PR rather than guessing.

### 4. Common failure modes and fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| `scalafmtCheckAll` / `scalafmtCheck` fails in CI/locally | Hand-edited Scala file not formatted | Run `sbt scalafmt` then re-run the check command |
| `test` fails after a version bump | New Jackson release changed exception message text/format that `JsonLint.scala`'s regex/parsing relies on (see the `duplicate` regex and message-cleanup logic in `parseException`) | Inspect the new Jackson version's `JsonParseException` message format and adjust `JsonLint.scala` or the fixtures under `docs/tests`/`docs/multiple-tests` accordingly |
| `plugins_test` fails on a fixture in `docs/tests` or `docs/multiple-tests` | Expected line/message in `results.xml` or fixture JSON no longer matches actual output | Re-verify the new output is correct, then update the fixture's expected results to match |

### 5. Definition of done

- Jackson version bumped in `src/main/resources/docs/patterns.json` (and any other files the task scope required, per the table above).
- Local `scalafmtCheckAll`/`test` commands pass.
- Docker image builds successfully via `docker:publishLocal`.
- `codacy-plugins-test` fixtures pass locally against the freshly built image.
- **After pushing and opening/updating the PR, every CI check on it is green.** Poll `gh pr checks <pr-url>` and iterate on any failure (fetch the real CI log, fix, push, re-poll) until all pass — a passing local build is not sufficient, because the CI toolchain can differ from your local one (see step 9).

## What is Codacy?

[Codacy](https://www.codacy.com/) is an Automated Code Review Tool that monitors your technical debt, helps you improve your code quality, teaches best practices to your developers, and helps you save time in Code Reviews.

### Among Codacy’s features:

-   Identify new Static Analysis issues
-   Commit and Pull Request Analysis with GitHub, BitBucket/Stash, GitLab (and also direct git repositories)
-   Auto-comments on Commits and Pull Requests
-   Integrations with Slack, HipChat, Jira, YouTrack
-   Track issues in Code Style, Security, Error Proneness, Performance, Unused Code and other categories

Codacy also helps keep track of Code Coverage, Code Duplication, and Code Complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

### Free for Open Source

Codacy is free for Open Source projects.
