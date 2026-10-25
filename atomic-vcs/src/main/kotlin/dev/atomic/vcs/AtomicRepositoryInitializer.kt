package dev.atomic.vcs

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.vcs.VcsRepositoryInitializer
import dev.atomic.core.cli.AtomicCliImpl
import kotlinx.coroutines.runBlocking
import java.io.File

class AtomicRepositoryInitializer : VcsRepositoryInitializer {
    override fun getSupportedVcs(): VcsKey = AtomicVcs.getKey()

    override fun initRepository(file: File) {
        val cli = AtomicCliImpl()
        val success = runBlocking {
            cli.init(file.toPath())
        }
        if (!success) {
            throw VcsException("Failed to initialize Atomic repository in ${file.absolutePath}")
        }
    }
}
