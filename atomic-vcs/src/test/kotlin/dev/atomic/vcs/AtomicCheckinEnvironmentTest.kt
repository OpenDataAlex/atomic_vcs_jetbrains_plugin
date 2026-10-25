package dev.atomic.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.process.ProcessResult
import dev.atomic.core.test.MockProcessRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Path

class AtomicCheckinEnvironmentTest {
    @TempDir
    lateinit var tempDir: Path

    private val runner = MockProcessRunner()

    private fun projectWithBasePath(basePath: String?): Project = Proxy.newProxyInstance(
        Project::class.java.classLoader,
        arrayOf(Project::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getBasePath" -> basePath
            "isDisposed" -> false
            else -> null
        }
    } as Project

    private fun environment(basePath: String?) = AtomicCheckinEnvironment(
        projectWithBasePath(basePath),
        AtomicCliImpl(processRunner = runner),
    )

    private fun revision(path: Path) = object : ContentRevision {
        override fun getContent(): String? = null

        override fun getFile(): FilePath = LocalFilePath(path.toString(), false)

        override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
    }

    private fun newFileChange(name: String) = Change(null, revision(tempDir.resolve(name)))

    @Test
    fun operationNameIsRecord() {
        assertEquals("Record", environment(tempDir.toString()).checkinOperationName)
    }

    @Test
    fun refreshAfterRecordIsNeeded() {
        assertTrue(environment(tempDir.toString()).isRefreshAfterCommitNeeded)
    }

    @Test
    fun recordWithoutProjectPathDoesNothing() {
        val result = environment(null).commit(listOf(newFileChange("a.txt")), "msg", CommitContext(), mutableSetOf())

        assertNull(result)
        assertTrue(runner.recordedCommands.isEmpty())
    }

    @Test
    fun failedRecordIsReportedWithTheCliMessage() {
        runner.onCommand("record", ProcessResult(exitCode = 2, stdout = "", stderr = "boom: nothing to record"))

        val result = environment(tempDir.toString()).commit(listOf(newFileChange("a.txt")), "msg")

        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertTrue(result[0].message.contains("boom: nothing to record"), result[0].message)
    }

    @Test
    fun newFilesAreAddedBeforeTheyAreRecorded() {
        runner.onCommand("record", ProcessResult(exitCode = 2, stdout = "", stderr = "stop here"))

        environment(tempDir.toString()).commit(listOf(newFileChange("new.txt")), "msg")

        val subcommands = runner.recordedCommands.mapNotNull { cmd -> cmd.firstOrNull { it == "add" || it == "record" } }
        assertEquals(listOf("add", "record"), subcommands)
        val record = runner.recordedCommands.first { "record" in it }
        assertTrue(record.containsAll(listOf("-m", "msg", "--", "new.txt")), record.toString())
    }

    @Test
    fun filesStillPendingAfterRecordAreReported() {
        runner.onCommand("record", ProcessResult(exitCode = 0, stdout = "[dev 1/ABC] msg\n new.txt\n", stderr = ""))
        runner.onCommand(
            "status",
            ProcessResult(
                exitCode = 0,
                stdout = """{"view":"dev","clean":false,"entries":[{"path":"new.txt","status":"added","code":"A","details":null}]}""",
                stderr = "",
            ),
        )

        val result = environment(tempDir.toString()).commit(listOf(newFileChange("new.txt")), "msg")

        assertNotNull(result)
        assertTrue(result!![0].message.contains("did not record"), result[0].message)
        assertTrue(result[0].message.contains("new.txt"), result[0].message)
    }
}
