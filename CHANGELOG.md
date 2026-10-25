# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

First development version (`1.0.0-SNAPSHOT`). Developed and tested against `atomic` CLI v0.20.0. Nothing has been released yet.

### Added

- Atomic registered as a distributed VCS, so the **VCS** menu, the Commit tool window and file statuses work.
- Automatic mapping of the project root to Atomic when it has no VCS mapping. If the root is mapped to another VCS, a **Use Atomic** notification offers to switch.
- File statuses (modified, added, deleted, unversioned) in the project view and the Commit tool window, read from `atomic status --json`, with a fallback to `atomic status --short --no-color`.
- An **Atomic** submenu in the **VCS** menu with **Refresh Status**, **Record Changes...** and **Unrecord Latest Change...**. It only shows when the project root is mapped to Atomic.
- Recording changes from the Commit tool window with `atomic add` and `atomic record`. The plugin checks afterwards that the files were recorded, reports the CLI's error if they were not, and refreshes file statuses.
- **Create Atomic Repository...** action (`atomic init`).
- Status bar widget that shows the current Atomic view.
- A diff viewer that compares the working copy with the file's last recorded text.
- Atomic change graph in the Version Control **Log** tab. Dependencies between changes are the graph edges and views appear as branch labels. The list of changed files is shown for each change.
- AI provenance in the **Log** tab: agent, vendor, model, tool, session and turn, tokens, cost, goal and the agent's steps.
- MCP server for AI assistants, with automatic registration for AI Assistant (`.idea/llm.mcpServers.xml`) and Junie (`.junie/mcp.json`). It also includes AI agent session tracking and optional draft-view isolation of agent turns.
- A settings page under **Settings | Version Control | Atomic**: executable path with test and auto-detect, default parent view, MCP registration and agent session isolation.
- Unit tests for the CLI wrapper, parsers, diff reconstruction, provenance formatting, process runner, exceptions and the record flow.
- Code quality tooling: Spotless with ktlint, detekt with per-module baselines, Kotlin compiler warnings as errors in CI (`-PwarningsAsErrors=true`), Kover coverage with a minimum line-coverage check and a GitHub Actions workflow.
- `README.md`, `CONTRIBUTING.md` and this changelog.

### Changed

- User-facing wording says "record" instead of "commit" wherever the plugin controls the text. Labels built into the IDE, such as the Commit tool window title, still say "Commit".
- The Atomic settings page is registered once in `plugin.xml`.

### Fixed

- Statuses and the Commit tool window were empty because the parser expected a different JSON layout than the CLI returns. It now reads the `entries` list.
- Recording from the Commit tool window did nothing. The IDE called a `CheckinEnvironment.commit` overload that the plugin had not implemented, so the platform reported success without running `atomic record`.
- `atomic record` was called with `--json`, which the CLI rejects. The flag was removed.
- Spawned CLI processes could hang until the timeout waiting for input. Their stdin is now closed.
- `atomic log` and `atomic change` were called with the rejected `--json` flag. They now use `--format json`.
- The view list parser read the wrong fields from `atomic view list --json`.
- History loading failed for changes written by AI agents, because `authors` can be objects and not only strings.
- The "original" side of the diff viewer showed raw `atomic diff` output with ANSI color codes. It now shows the last recorded text of the file.

[Unreleased]: https://github.com/OpenDataAlex/atomic_vcs_jetbrains_plugin/commits/main
