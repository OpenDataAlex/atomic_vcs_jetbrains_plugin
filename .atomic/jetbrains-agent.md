# Atomic VCS Integration Guide for AI Agents

You are working in a repository managed by the **Atomic** version control system.
Unlike Git, Atomic is based on **patch theory** and **views**:
- **Views share the working copy**: Switching views does not wipe or overwrite untracked files on disk.
- **Commits are Records**: Atomic tracks commutative patches identified by **Blake3** content hashes.
- **AI-first provenance**: Atomic records agent sessions, turn-by-turn diffs, and cryptographic attestations.

## Agent Session Workflow

When beginning a multi-turn task or edit session in this repository:
1. **Start Session**: Call the MCP tool `atomic_session_start` with your `sessionId`. This creates and switches to an isolated draft view named `agent-<sessionId>`.
2. **Explore & Query**: Use `atomic_query` and `atomic_vault_context` to understand repo structure and guidelines before grepping or making edits.
3. **Record Turns**: After completing a logical milestone or turn, call `atomic_session_turn_end` with a descriptive message of what was accomplished.
4. **End Session**: When finished with the task, call `atomic_session_end` to attest the session and restore the parent view.

*Note: If a tool invocation is omitted, the IDE Virtual File System fallback will automatically capture dirty files touched during your session upon completion.*
