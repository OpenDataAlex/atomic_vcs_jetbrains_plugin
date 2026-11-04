# Atomic VCS Integration Guide for AI Agents

You are working in a repository managed by the **Atomic** version control system.
Unlike Git, Atomic is based on **patch theory** and **views**:
- **Views share the working copy**: Switching views does not wipe or overwrite untracked files on disk.
- **Commits are Records**: Atomic tracks commutative patches identified by **Blake3** content hashes.
- **AI-first provenance**: Atomic records agent sessions, turn-by-turn diffs, and cryptographic attestations.

## Agent Session Workflow

Follow the rework-reduction loop from <https://docs.atomic.dev/guides/reduce-ai-agent-rework>:
define the intent, load only the context you need, check scope after every turn, and leave promotion to the reviewer.

1. **Read or create one intent first.** Run `atomic intent list` and `atomic intent show <id>`. If none covers the task, ask the user for one or scaffold it with `atomic intent new`.
   Acceptance criteria must be observable (a test, command output, a file or behavior), not subjective ("improve error handling"). Do not start editing before the affected behavior, files, checks and exclusions are named.
2. **Start Session**: Call the MCP tool `atomic_session_start` with your `sessionId`. This creates and switches to an isolated draft view named `agent-<sessionId>`.
3. **Load targeted context.** Call `atomic_vault_context` first, then narrow with `atomic_query` (code, entities, neighbors) before grepping. Stop gathering context once the affected behavior, files and checks are clear.
4. **Record Turns**: After each logical milestone, call `atomic_session_turn_end` with a descriptive message. The reply lists the files changed against the parent view and warns about deletions, renames and large edits.
   Compare it with the intent's scope. If it drifted, stop and tell the user before continuing.
5. **Keep durable lessons, not transcripts.** Store a decision or constraint that future work needs with `atomic memory new` (see `atomic memory kinds`). Do not copy the chat.
6. **End Session**: When finished, call `atomic_session_end` to attest the session and restore the parent view.
7. **Never promote your own work.** Do not run `atomic insert` into the target view. The reviewer promotes the reviewed change with **VCS | Atomic | Insert Change into View...** (also in the Log tab context menu).

*Note: If a tool invocation is omitted, the IDE Virtual File System fallback will automatically capture dirty files touched during your session upon completion. Read-only turns record nothing, and some agents do not report token or cost data; treat missing usage fields as "not reported", not as zero.*
