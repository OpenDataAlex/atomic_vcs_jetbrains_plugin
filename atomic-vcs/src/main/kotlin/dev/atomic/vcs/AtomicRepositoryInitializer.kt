package dev.atomic.vcs

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.vcs.VcsRepositoryInitializer
import dev.atomic.core.cli.AtomicCliImpl
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path

class AtomicRepositoryInitializer : VcsRepositoryInitializer {
    override fun getSupportedVcs(): VcsKey = AtomicVcs.getKey()

    override fun initRepository(file: File) {
        initRepository(file.toPath())
    }

    @Throws(VcsException::class)
    fun initRepository(rootDir: Path) {
        val cli = AtomicCliImpl()
        val success = runBlocking {
            cli.init(rootDir)
        }
        if (!success) {
            throw VcsException("Failed to initialize Atomic repository in $rootDir")
        }
    }
}
