# Contributing

Thanks for helping improve the Atomic plugin for JetBrains IDEs. This document explains how to set up, change and submit work.

By contributing you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).

## Ways to contribute

- **Report bugs**: open an [issue](https://github.com/OpenDataAlex/atomic_vcs_jetbrains_plugin/issues).
- **Suggest features**: open an issue describing the problem you want solved.
- **Send code or docs**: open a pull request.

### Reporting a bug

Please include:

- IDE name and version (**Help | About**), plugin version and OS.
- `atomic --version` and the output of `atomic status` in the project directory.
- What you did, what you expected and what happened.
- Relevant lines from `idea.log` (**Help | Show Log in Files**). The plugin logs under `dev.atomic`.

Remove anything private before pasting logs or repository output.

## Development setup

### Prerequisites

- JDK 17
- The [`atomic`](https://docs.atomic.dev) CLI on your `PATH` (tested with v0.20.0)
- IntelliJ IDEA (Community or Ultimate) with the Kotlin plugin
- Network access for the first build, which downloads the IntelliJ Platform

### Get the code

```bash
git clone https://github.com/OpenDataAlex/atomic_vcs_jetbrains_plugin.git
cd atomic_vcs_jetbrains_plugin
```

Open the project in IntelliJ IDEA as a Gradle project.

`gradle.properties` sets `org.gradle.java.home` to `/usr/lib/jvm/java-17-openjdk-amd64`. If your JDK 17 is elsewhere, point it there locally and don't commit that change.

### Build, test and run

```bash
./gradlew test           # unit tests for all modules
./gradlew buildPlugin    # builds build/distributions/atomic-intellij-*.zip
./gradlew runIde         # sandbox IDE with the plugin installed
```

Use `--offline` after the first build to skip network lookups. To test in a real project, create a scratch repository, then open it in the sandbox IDE:

```bash
mkdir /tmp/atomic-scratch && cd /tmp/atomic-scratch
atomic init
```

### Set up code quality tooling

The formatter, linter and coverage tools are Gradle plugins, so they need no separate install. The first `./gradlew` run downloads them. To check that everything works:

```bash
./gradlew spotlessCheck detekt test koverVerify
```

In IntelliJ IDEA, the code style comes from `.editorconfig`, which the IDE picks up automatically. Run `./gradlew spotlessApply` to fix formatting before you commit. CI runs the same checks on your pull request, so run them locally first. See [Code quality](#code-quality) for what each tool does.

## Architecture

| Module | What goes there |
| --- | --- |
| `atomic-core` | Code with no IntelliJ dependency: the `AtomicCli` interface and `AtomicCliImpl`, `DefaultProcessRunner`, `AtomicParsers`, models, `RecordedContentReconstructor`, `ProvenanceFormatter`. |
| `atomic-vcs` | IntelliJ integration: VCS class, change/diff providers, `AtomicCheckinEnvironment`, log provider, actions, settings, status bar widget. |
| `atomic-ai` | MCP server, MCP config registration, agent session tracking. |
| `src/main/resources/META-INF/plugin.xml` | Registers extensions, actions and listeners. |

Guidelines:

- Put anything that talks to the `atomic` process, or parses its output, in `atomic-core` so it can be unit-tested without an IDE.
- `atomic-vcs` and `atomic-ai` call the CLI through `AtomicCli`. Don't spawn processes directly from IDE code.
- Don't run CLI calls on the UI thread.
- Register new extensions and actions in `plugin.xml`.

## Working with the Atomic CLI

The CLI's output formats change between versions, so parsers are the most fragile part of the plugin. Some lessons learned:

- **Check real output before writing a parser.** Run the command in a scratch repo and copy the actual output into your test. Don't write tests from assumptions about the format.
- Prefer machine-readable output (`--json`, or `--format json` for `log` and `change`) and `--no-color`. Not every command accepts `--json`. `atomic record` rejects it, for example.
- Stdin of spawned processes is closed so a CLI waiting for input can't hang the IDE.
- Be tolerant of missing or extra fields. A parser that silently decodes to empty lists is worse than one that fails loudly.
- When you add support for a new CLI version, note it in your pull request.

## Code quality

The project uses the Kotlin equivalents of ruff and mypy:

| Python tool | Here | What it does | Command |
| --- | --- | --- | --- |
| `ruff format` / `ruff check` (style) | [Spotless](https://github.com/diffplug/spotless) + [ktlint](https://pinterest.github.io/ktlint/) | Formats and lints Kotlin code. Rules live in `.editorconfig`. | `./gradlew spotlessCheck` / `./gradlew spotlessApply` |
| `ruff check` (bugs, complexity) | [detekt](https://detekt.dev) | Static analysis. Rules live in `config/detekt/detekt.yml`. Known legacy findings are recorded in each module's `detekt-baseline.xml`. | `./gradlew detekt` |
| `mypy` | Kotlin compiler | Kotlin is statically typed. CI compiles with warnings as errors. | `./gradlew compileKotlin compileTestKotlin -PwarningsAsErrors=true` |
| `pytest --cov` | JUnit 5 + [Kover](https://github.com/Kotlin/kotlinx-kover) | Unit tests and a minimum line-coverage check. | `./gradlew test koverVerify` |

Run everything CI runs with `./gradlew spotlessCheck detekt test koverVerify`.

### Baselines and coverage

- New code must pass detekt. Don't add findings to a `detekt-baseline.xml` to get a change through. Fix the finding, or discuss relaxing the rule in the PR. To regenerate a baseline after you've fixed old findings, run `./gradlew detektBaseline`.
- Open the coverage report with `./gradlew koverHtmlReport` (`build/reports/kover/html`). The build fails if total line coverage drops below the limit set in `build.gradle.kts`. Raise the limit when coverage goes up.
- Compiler warnings are fine locally but fail CI. If you must override a deprecated platform method, add a narrow `@Suppress` with a comment.

## Tests

- Add or update unit tests for any behavior change. Parser changes need a test using real CLI output.
- Core tests use `MockProcessRunner` to fake the CLI. See `AtomicCliImplTest` for the pattern. Code that needs an IDE `Project` can be tested with a stand-in for the interface, as in `AtomicCheckinEnvironmentTest`.
- Tests use JUnit 5 and `kotlinx-coroutines-test` (`runTest`) for suspend functions.
- Name tests after the behavior they check, for example `failedRecordIsReportedWithTheCliMessage`.
- Run `./gradlew test` before opening a pull request. Mention in the PR if you checked the change in a running IDE. IDE behavior is not covered by the unit tests.

## Pull requests

1. Fork the repository and create a branch from `main`, for example `fix/status-parsing`.
2. Keep each pull request focused on one change.
3. Add tests and update the [README](README.md) if behavior or settings change. Add a line under **Unreleased** in the [CHANGELOG](CHANGELOG.md) for any change users would notice.
4. Make sure `./gradlew spotlessCheck detekt test koverVerify buildPlugin` passes. CI runs the same checks.
5. Open the PR and describe what changed, why, and how you tested it. Link related issues.

Commit messages should be short and written in the imperative mood (for example, "Fix status parsing for untracked files"), with details in the body if needed.

## Changelog

The [CHANGELOG](CHANGELOG.md) follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

- Add entries under `## [Unreleased]`, in the matching group: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed` or `Security`.
- Write for users of the plugin, not for developers. Say what changed, not which classes were touched.
- When releasing, rename `Unreleased` to the version and date (`## [1.0.0] - YYYY-MM-DD`), start a new empty `Unreleased` section, and update the links at the bottom of the file.

## Project hygiene

- Don't commit build output (`build/`, `.gradle/`, `.intellijPlatform/`), IDE-local files, or personal paths.
- Don't commit secrets, tokens, or logs containing private repository content.

## Questions

Open an issue if something here is unclear. Improvements to this document are welcome too.
