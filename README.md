# Atomic VCS for JetBrains IDEs

A JetBrains IDE plugin that integrates the [Atomic](https://docs.atomic.dev) version control tool (patch-theory based, with views and AI-agent provenance) into PyCharm, RustRover and other IntelliJ-based IDEs.
This was vibe-coded to hell and back because I wanted to test Atomic while also having it properly integrated into JetBrains IDEs. It's not perfect, but it's a start.

> **Status:** early development (`0.1.0`). It is developed and tested against `atomic` CLI **v0.20.0**. Expect rough edges, and please [open an issue](https://github.com/OpenDataAlex/atomic_vcs_jetbrains_plugin/issues) when you find one.

## Features

- **VCS integration**: Atomic shows up as a distributed VCS. It works with the **VCS** menu, the Commit tool window and the project view.
- **File statuses**: modified, added, deleted and unversioned files get the usual status colors in the project view and the Commit tool window.
- **Record changes**: the Commit tool window records changes with `atomic add` + `atomic record`. Atomic calls this *record*, so the plugin does too (**VCS | Atomic | Record Changes...**). The IDE's own labels, such as the tool window title, still say "Commit".
- **Unrecord**: **VCS | Atomic | Unrecord Latest Change...** removes the latest change from the current view.
- **Diffs**: the diff viewer compares your working copy with the file's last recorded text.
- **Version graph**: the Version Control **Log** tab shows the Atomic change graph. Dependencies between changes are the graph edges, and views appear as branch labels.
- **AI provenance**: for changes recorded by AI agents, the Log tab shows the agent, vendor, model, tool, session and turn, tokens, cost, goal and the agent's steps.
- **Repository creation**: **Create Atomic Repository...** runs `atomic init` for a directory.
- **Git shim**: **VCS | Atomic | Enable Git Shim...** (plus Status, Verify, Reconcile, Disable) wraps [`atomic git shim`](https://docs.atomic.dev/getting-started/git-shim) so a colocated `.git` and `.atomic` stay in agreement. Map the IDE directory to Atomic; Git remains on disk for GitHub/GitLab.
- **Promote reviewed changes**: **VCS | Atomic | Insert Change into View...** (also in the Log tab context menu) runs `atomic insert --view <target> <hash>`. Use it to promote a reviewed agent change from an `agent-*` draft view.
- **View widget**: the status bar shows the current Atomic view.
- **AI assistant integration**: the plugin registers an Atomic [MCP](https://modelcontextprotocol.io) server for JetBrains AI Assistant and Junie, and can isolate agent turns in draft views.
- **Agent integrations**: per-project controls run `atomic agent enable`, `disable` and `status` for an explicitly selected agent; integrations are never enabled automatically.

## Requirements

- A JetBrains IDE based on platform build **251** (2025.1) or newer.
- The [`atomic`](https://docs.atomic.dev) CLI, on your `PATH` or configured in the plugin settings.
- To build from source: JDK 17.

## Installation

The plugin is not published to the JetBrains Marketplace yet. Install it from a local build:

1. Build the plugin (see [Building](#building)). This produces `build/distributions/atomic-intellij-1.0.0-SNAPSHOT.zip`.
2. In the IDE, open **Settings | Plugins**, click the gear icon, then **Install Plugin from Disk...**.
3. Select the zip and restart the IDE.

## Getting started

1. Open a project that is an Atomic repository (it contains a `.atomic` folder), or create one with **VCS | Create Atomic Repository...**.
2. If the project root is mapped to another VCS (for example Git), the plugin shows a **Use Atomic** notification. Click it, or change the mapping in **Settings | Version Control | Directory Mappings**. A directory can only have one VCS mapping at a time. For a [Git shim](https://docs.atomic.dev/getting-started/git-shim) repo, keep the mapping on Atomic and use **VCS | Atomic | Enable Git Shim...**.
3. Edit files. Their status colors update in the project view.
4. Open the Commit tool window, select changes, write a message and click **Record**.
5. Open the **Log** tab under Version Control to browse the change graph.

If the `atomic` binary can't be found you will see a "CLI Not Found" warning. Set the path in the settings below.

## Settings

Open **Settings | Version Control | Atomic** for VCS/MCP settings, or **Atomic Agent Integrations** for agent hooks.

| Setting | Description |
| --- | --- |
| Atomic Executable Path | Path to the `atomic` binary. Leave empty to use `PATH`. **Test / Auto-detect** checks the path. |
| Default Parent View | View used as the parent for AI agent draft views. Defaults to `main`. |
| Automatically register Atomic STDIO MCP server for AI Assistant and Junie | Writes MCP config for AI Assistant (`.idea/llm.mcpServers.xml`) and Junie (`.junie/mcp.json`) when the project has a `.atomic` folder. Existing files are never overwritten. |
| Enable AI Agent session isolation (split draft views on turn start) | Splits draft views at the start of an agent turn. |
| Atomic Agent Integrations | Select an agent ID and run `atomic agent enable`, `disable` or verbose `status` in a chosen directory. |

## AI agent support

When `.atomic` exists in the project, the plugin points your AI tools at `atomic mcp`. The plugin's own MCP server code (`atomic-ai`) exposes these tools:

`atomic_status`, `atomic_diff`, `atomic_log`, `atomic_change`, `atomic_query`, `atomic_vault_context`, `atomic_provenance`, `atomic_session_start`, `atomic_session_turn_end`, `atomic_session_end`, `atomic_attest`.

The `atomic_session_turn_end` and `atomic_session_end` replies include a **scope check**: the files the draft view changed compared with its parent view (`atomic diff --from <parent> --to <draft>`), with warnings for deletions, renames and large edits.

### Recommended workflow

The plugin follows the loop from the Atomic guide [How to Reduce Rework from AI Coding Agents](https://docs.atomic.dev/guides/reduce-ai-agent-rework):

1. Define one intent with observable acceptance criteria (`atomic intent new`, from a terminal).
2. Let the agent load targeted context (`atomic_vault_context`, then `atomic_query`).
3. Run the agent in a draft view (**AI Agent session isolation**) and check the scope report after each turn.
4. Review the change in the Log tab, including provenance.
5. Promote only the reviewed change with **Insert Change into View...**.

The agent instructions for this workflow are in [`.atomic/jetbrains-agent.md`](.atomic/jetbrains-agent.md). Intent and memory commands are not wrapped by the plugin yet.

### Atomic hook integrations and Air

Open **Settings | Version Control | Atomic Agent Integrations** to select a documented Atomic agent integration or enter another ID supported by the installed CLI. The directory defaults to the project root, and enable requires confirmation because Atomic may fetch integration files and modify project or shared agent configuration. The page displays the CLI's output; it does not automatically detect or enable agents.

Atomic currently documents integrations such as [Claude Code](https://docs.atomic.dev/agents/installing-agent-integrations) (`claude-code`) and Codex (`codex`). Codex hook support is experimental. Installing an integration for Claude Code or Codex does not establish that the corresponding agent surface inside JetBrains Air uses those hooks. In particular, compatibility with Claude Agent, Junie and arbitrary ACP agents is not promised by this plugin; MCP support is a separate integration path.

Provenance recorded with changes appears in the Log tab when you select a change. Changes without agent data show nothing extra.

## Building

```bash
./gradlew buildPlugin          # builds build/distributions/atomic-intellij-*.zip
./gradlew test                 # runs the unit tests of all modules
./gradlew spotlessApply        # formats the code (ktlint)
./gradlew check koverVerify    # lint, static analysis (detekt), tests and the coverage floor
./gradlew runIde               # starts a sandbox IDE with the plugin
```

The first build downloads the IntelliJ Platform, so it needs network access. Add `--offline` once dependencies are cached. `gradle.properties` pins the JDK to `/usr/lib/jvm/java-17-openjdk-amd64`. Change `org.gradle.java.home` if your JDK is elsewhere.

## Project layout

| Module | Purpose |
| --- | --- |
| `atomic-core` | IDE-independent code: the `atomic` CLI wrapper, process runner, output parsers, data models, diff reconstruction and provenance formatting. |
| `atomic-vcs` | IntelliJ VCS integration: `AtomicVcs`, change and diff providers, record (check-in) environment, log provider, actions, settings and status bar widget. |
| `atomic-ai` | AI integration: MCP server, MCP registration and agent session tracking. |
| `src/main/resources/META-INF/plugin.xml` | Plugin descriptor that registers everything with the IDE. |

## Troubleshooting

- **No file colors or an empty Commit window**: make sure the root is mapped to Atomic and that `atomic status` works in a terminal in the same directory. A `.atomic` folder that isn't a real repository reports "Not in an Atomic repository".
- **The IDE uses a different `atomic` than your shell**: set the full path in the settings.
- **Log tab is empty**: look for "Failed to read the Atomic change graph" in `idea.log` (**Help | Show Log in Files**).
- **Record does nothing**: check `idea.log` for "atomic record invoked".

## Known limitations

- Atomic can't provide file content for past changes, so diffs of historical changes in the Log tab have no file content.
- The "original" side of a diff is rebuilt from the file on disk, so unsaved editor changes aren't included in it.
- The commit hash column in the Log shows a hex form of the hash, not Atomic's base32 form.
- Log filtering supports text search only.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, the code quality tooling (ktlint, detekt, Kover) and the pull request process.

## Changelog

Notable changes are listed in [CHANGELOG.md](CHANGELOG.md).

## License

Licensed under the [Apache License 2.0](LICENSE).
