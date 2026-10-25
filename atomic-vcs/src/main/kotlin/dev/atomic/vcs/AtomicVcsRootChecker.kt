package dev.atomic.vcs

import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class AtomicVcsRootChecker : VcsRootChecker() {
    override fun getSupportedVcs(): VcsKey = AtomicVcs.getKey()

    override fun isRoot(file: VirtualFile): Boolean {
        val dotAtomic = file.findChild(AtomicVcs.DOT_DIR)
        if (dotAtomic != null && dotAtomic.isDirectory) return true
        return File(file.path, AtomicVcs.DOT_DIR).isDirectory
    }

    override fun isRoot(path: String): Boolean = File(path, AtomicVcs.DOT_DIR).isDirectory

    override fun validateRoot(file: VirtualFile): Boolean = isRoot(file)

    override fun validateRoot(path: String): Boolean = isRoot(path)

    override fun isVcsDir(path: String): Boolean = AtomicVcs.DOT_DIR.equals(path, ignoreCase = true) ||
        path.endsWith("/${AtomicVcs.DOT_DIR}") ||
        path.endsWith("\\${AtomicVcs.DOT_DIR}")
}
