package dev.atomic.core.parser

import dev.atomic.core.model.AtomicChangeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class AtomicParsersTest {
    private val repoRoot = Paths.get("/repo")

    @Test
    fun testParseStatusJson() {
        val jsonOutput = """
            {
                "view": "main",
                "modified": ["src/main.rs", "Cargo.toml"],
                "added": ["src/new_file.rs"],
                "deleted": ["old_file.txt"],
                "untracked": ["notes.md"],
                "conflicted": ["src/conflict.rs"]
            }
        """.trimIndent()

        val result = AtomicParsers.parseStatus(jsonOutput, repoRoot)
        assertEquals("main", result.currentView)
        assertEquals(listOf(repoRoot.resolve("src/main.rs"), repoRoot.resolve("Cargo.toml")), result.modified)
        assertEquals(listOf(repoRoot.resolve("src/new_file.rs")), result.added)
        assertEquals(listOf(repoRoot.resolve("old_file.txt")), result.deleted)
        assertEquals(listOf(repoRoot.resolve("notes.md")), result.untracked)
        assertEquals(listOf(repoRoot.resolve("src/conflict.rs")), result.conflicted)
        assertFalse(result.isClean)
    }

    @Test
    fun testParseStatusJsonEntries() {
        val jsonOutput = """
            {
              "schema_version": 1,
              "repository_root": "/repo",
              "view": "dev",
              "state": "ABC",
              "clean": false,
              "entries": [
                {"path": "a.txt", "status": "modified", "code": "M", "details": null},
                {"path": "d/c.txt", "status": "added", "code": "A", "details": null},
                {"path": "gone.txt", "status": "deleted", "code": "D", "details": null},
                {"path": "new.txt", "status": "untracked", "code": "?", "details": null}
              ]
            }
        """.trimIndent()

        val result = AtomicParsers.parseStatus(jsonOutput, repoRoot)
        assertEquals("dev", result.currentView)
        assertEquals(listOf(repoRoot.resolve("a.txt")), result.modified)
        assertEquals(listOf(repoRoot.resolve("d/c.txt")), result.added)
        assertEquals(listOf(repoRoot.resolve("gone.txt")), result.deleted)
        assertEquals(listOf(repoRoot.resolve("new.txt")), result.untracked)
        assertFalse(result.isClean)
    }

    @Test
    fun testParseStatusShort() {
        val result = AtomicParsers.parseStatus("A  d/c.txt\nM  a.txt\n?? new.txt\n", repoRoot)
        assertEquals(listOf(repoRoot.resolve("a.txt")), result.modified)
        assertEquals(listOf(repoRoot.resolve("d/c.txt")), result.added)
        assertEquals(listOf(repoRoot.resolve("new.txt")), result.untracked)
    }

    @Test
    fun testParseStatusText() {
        val textOutput = """
            On view: feature-auth
            Modified:
              - src/auth.rs
            Added:
              - src/token.rs
            Deleted:
              - src/old_auth.rs
            Untracked:
              - temp.log
            Conflicted:
              - config.toml
        """.trimIndent()

        val result = AtomicParsers.parseStatus(textOutput, repoRoot)
        assertEquals("feature-auth", result.currentView)
        assertEquals(listOf(repoRoot.resolve("src/auth.rs")), result.modified)
        assertEquals(listOf(repoRoot.resolve("src/token.rs")), result.added)
        assertEquals(listOf(repoRoot.resolve("src/old_auth.rs")), result.deleted)
        assertEquals(listOf(repoRoot.resolve("temp.log")), result.untracked)
        assertEquals(listOf(repoRoot.resolve("config.toml")), result.conflicted)
    }

    @Test
    fun testParseDiff() {
        val rawDiff = """
            diff --atomic a/src/main.rs b/src/main.rs
            --- a/src/main.rs
            +++ b/src/main.rs
            @@ -1,3 +1,4 @@
             fn main() {
            +    println!("Hello Atomic!");
             }
        """.trimIndent()

        val result = AtomicParsers.parseDiff(rawDiff, repoRoot)
        assertEquals(1, result.fileDiffs.size)
        val fileDiff = result.fileDiffs.first()
        assertEquals(repoRoot.resolve("src/main.rs"), fileDiff.oldPath)
        assertEquals(repoRoot.resolve("src/main.rs"), fileDiff.newPath)
        assertEquals(1, fileDiff.hunks.size)
    }

    @Test
    fun testParseLogJson() {
        val jsonOutput = """
            [
                {
                    "hash": "bafkr4ihdwdcefghij12345",
                    "message": "Implement token validation",
                    "author": "Alice <alice@atomic.dev>",
                    "timestamp": "2026-10-24T08:00:00Z",
                    "is_draft": false
                },
                {
                    "hash": "bafkr4ihdraft9876543210",
                    "message": "WIP Agent turn 1",
                    "author": "Junie <junie@jetbrains.com>",
                    "timestamp": "2026-10-24T08:15:00Z",
                    "is_draft": true
                }
            ]
        """.trimIndent()

        val summaries = AtomicParsers.parseLog(jsonOutput)
        assertEquals(2, summaries.size)
        assertEquals("bafkr4ihdwdcefghij12345", summaries[0].hash)
        assertEquals("Implement token validation", summaries[0].message)
        assertFalse(summaries[0].isDraft)
        assertEquals("bafkr4ihdraft9876543210", summaries[1].hash)
        assertTrue(summaries[1].isDraft)
    }

    @Test
    fun testParseChangeJson() {
        val jsonOutput = """
            {
                "hash": "bafkr4ihdwdcefghij12345",
                "message": "Add user authentication service",
                "author": "Bob <bob@atomic.dev>",
                "timestamp": "2026-10-24T08:20:00Z",
                "is_draft": false,
                "parents": ["parent123"],
                "files": [
                    {"path": "src/auth.rs", "status": "M"},
                    {"path": "src/user.rs", "status": "A"}
                ],
                "raw_patch": "..."
            }
        """.trimIndent()

        val detail = AtomicParsers.parseChange(jsonOutput, repoRoot)
        assertEquals("bafkr4ihdwdcefghij12345", detail.hash)
        assertEquals("Add user authentication service", detail.message)
        assertEquals(2, detail.changedFiles.size)
        assertEquals(AtomicChangeType.MODIFIED, detail.changedFiles[0].changeType)
        assertEquals(AtomicChangeType.ADDED, detail.changedFiles[1].changeType)
    }

    @Test
    fun testParseViewsJson() {
        val jsonOutput = """
            [
                {"name": "main", "current": true, "draft": false, "parent": null},
                {"name": "agent-session-1", "current": false, "draft": true, "parent": "main"}
            ]
        """.trimIndent()

        val views = AtomicParsers.parseViews(jsonOutput)
        assertEquals(2, views.size)
        assertEquals("main", views[0].name)
        assertTrue(views[0].isCurrent)
        assertFalse(views[0].isDraft)
        assertEquals("agent-session-1", views[1].name)
        assertFalse(views[1].isCurrent)
        assertTrue(views[1].isDraft)
        assertEquals("main", views[1].parentView)
    }

    @Test
    fun testParseViewsDocument() {
        val jsonOutput = """
            {
              "schema_version": 1,
              "current_view": "dev",
              "views": [
                {"name": "dev", "current": true, "scope": "shared", "parent": null, "change_count": 3},
                {"name": "wip", "current": false, "scope": "draft", "parent": "dev", "change_count": 1}
              ]
            }
        """.trimIndent()

        val views = AtomicParsers.parseViews(jsonOutput)
        assertEquals(listOf("dev", "wip"), views.map { it.name })
        assertTrue(views[0].isCurrent)
        assertTrue(views[1].isDraft)
        assertEquals("dev", views[1].parentView)
    }

    @Test
    fun testParseLogJsonWithSequence() {
        val jsonOutput = """
            [
              {"sequence": 2, "hash": "BBB", "state": "S2", "message": "second", "timestamp": "2026-10-25T16:55:06.98+00:00", "is_tagged": true},
              {"sequence": 1, "hash": "AAA", "state": "S1", "message": "first", "timestamp": "2026-10-24T13:44:48.06+00:00", "is_tagged": false}
            ]
        """.trimIndent()

        val log = AtomicParsers.parseLog(jsonOutput)
        assertEquals(listOf("BBB", "AAA"), log.map { it.hash })
        assertEquals(2L, log[0].sequence)
        assertTrue(log[0].isTagged)
        assertEquals("second", log[0].message)
    }

    @Test
    fun testParseChangeDependenciesAndHunks() {
        val jsonOutput = """
            {
              "hash": "BBB",
              "message": "second",
              "authors": ["Alex"],
              "timestamp": "2026-10-25T16:55:06+00:00",
              "dependencies": ["AAA"],
              "hunks": [
                {"hunk_type": "DirAdd", "path": "d"},
                {"hunk_type": "FileAdd", "path": "d/new.txt"},
                {"hunk_type": "Edit", "path": "d/new.txt"},
                {"hunk_type": "Edit", "path": "old.txt"}
              ]
            }
        """.trimIndent()

        val detail = AtomicParsers.parseChange(jsonOutput, repoRoot)
        assertEquals(listOf("AAA"), detail.parents)
        assertEquals("Alex", detail.author)
        assertEquals(
            listOf(repoRoot.resolve("d/new.txt") to AtomicChangeType.ADDED, repoRoot.resolve("old.txt") to AtomicChangeType.MODIFIED),
            detail.changedFiles.map { it.path to it.changeType },
        )
    }

    @Test
    fun testParseRecordJson() {
        val jsonOutput = """
            {
                "hash": "bafkr4ihrec123456",
                "message": "Add login endpoint",
                "files": ["src/login.rs", "src/routes.rs"]
            }
        """.trimIndent()

        val record = AtomicParsers.parseRecord(jsonOutput, repoRoot)
        assertEquals("bafkr4ihrec123456", record.changeHash)
        assertEquals("Add login endpoint", record.message)
        assertEquals(listOf(repoRoot.resolve("src/login.rs"), repoRoot.resolve("src/routes.rs")), record.filesRecorded)
    }

    @Test
    fun testParseQueryJson() {
        val jsonOutput = """
            {
                "query": "authentication token",
                "matches": [
                    {
                        "file": "src/auth.rs",
                        "line": 42,
                        "snippet": "pub fn validate_token() -> bool",
                        "score": 0.95
                    }
                ]
            }
        """.trimIndent()

        val query = AtomicParsers.parseQuery(jsonOutput, repoRoot)
        assertEquals(1, query.matches.size)
        assertEquals(repoRoot.resolve("src/auth.rs"), query.matches[0].path)
        assertEquals(42, query.matches[0].line)
        assertEquals("pub fn validate_token() -> bool", query.matches[0].snippet)
        assertEquals(0.95, query.matches[0].score)
    }

    @Test
    fun testParseProvenanceJson() {
        val jsonOutput = """
            {
                "hash": "bafkr4ihprovenance123",
                "agent": "junie",
                "session_id": "session-42",
                "turn": 3,
                "prompt": "Refactor auth helper",
                "model": "gemini-3.7-flash",
                "attestation": "attest-abc-99"
            }
        """.trimIndent()

        val prov = AtomicParsers.parseProvenance(jsonOutput, "bafkr4ihprovenance123")
        assertEquals("junie", prov.agent)
        assertEquals("session-42", prov.sessionId)
        assertEquals(3, prov.turn)
        assertEquals("Refactor auth helper", prov.prompt)
        assertEquals("gemini-3.7-flash", prov.model)
        assertEquals("attest-abc-99", prov.attestationHash)
    }

    @Test
    fun testParseAttestationJson() {
        val jsonOutput = """
            {
                "hash": "attest-bafkr4ih888",
                "timestamp": "2026-10-24T08:30:00Z",
                "signature": "sig-ed25519-xyz",
                "status": "verified"
            }
        """.trimIndent()

        val att = AtomicParsers.parseAttestation(jsonOutput)
        assertEquals("attest-bafkr4ih888", att.hash)
        assertEquals("2026-10-24T08:30:00Z", att.timestamp)
        assertEquals("sig-ed25519-xyz", att.signature)
        assertEquals("verified", att.status)
    }
}
