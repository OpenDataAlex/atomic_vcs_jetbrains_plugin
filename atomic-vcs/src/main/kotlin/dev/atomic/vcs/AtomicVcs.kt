package dev.atomic.vcs

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import dev.atomic.core.cli.AtomicCli
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.vcs.history.AtomicHistoryProvider
import dev.atomic.vcs.rollback.AtomicRollbackEnvironment
import dev.atomic.vcs.settings.AtomicSettings

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class AtomicVcs(project: Project) : AbstractVcs(project, NAME) {
    val cli: AtomicCli = AtomicCliImpl(executablePathProvider = { AtomicSettings.getInstance(project).executablePath })

    private val changeProvider = AtomicChangeProvider(project, cli)
    private val diffProvider = AtomicDiffProvider(project, cli)
    private val checkinEnv = AtomicCheckinEnvironment(project, cli)
    private val rollbackEnv = AtomicRollbackEnvironment(project, cli)
    private val historyProv = AtomicHistoryProvider(project, cli)

    override fun getDisplayName(): String = NAME

    // The settings page is registered via <projectConfigurable> in plugin.xml; returning one here as well would show it twice.
    override fun getConfigurable(): Configurable? = null

    // Atomic is a distributed (patch-based) VCS; the IDE only enables the non-modal Commit tool window for distributed VCSs.
    override fun getType(): VcsType = VcsType.distributed

    override fun getChangeProvider(): ChangeProvider? = if (project.isDefault) null else changeProvider

    override fun getDiffProvider(): DiffProvider? = if (project.isDefault) null else diffProvider

    override fun getCheckinEnvironment(): CheckinEnvironment? = if (project.isDefault) null else checkinEnv

    override fun getRollbackEnvironment(): RollbackEnvironment? = if (project.isDefault) null else rollbackEnv

    override fun getVcsHistoryProvider(): VcsHistoryProvider? = if (project.isDefault) null else historyProv

    override fun isVersionedDirectory(dir: com.intellij.openapi.vfs.VirtualFile): Boolean {
        val dotAtomic = dir.findChild(DOT_DIR)
        if (dotAtomic != null && dotAtomic.isDirectory) return true
        return java.io.File(dir.path, DOT_DIR).isDirectory
    }

    override fun arePartialChangelistsSupported(): Boolean = false

    override fun allowsNestedRoots(): Boolean = true

    override fun enableIntegration(targetDirectory: com.intellij.openapi.vfs.VirtualFile?) {
        com.intellij.util.concurrency.ThreadingAssertions
            .assertEventDispatchThread()
        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            object : com.intellij.openapi.progress.Task.Backgroundable(project, "Enabling Atomic Integration...", true) {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    AtomicIntegrationEnabler(this@AtomicVcs, targetDirectory).detectAndEnable()
                }
            },
        )
    }

    companion object {
        const val NAME = "Atomic"

        /** Name of the folder that marks the root of an Atomic repository. */
        const val DOT_DIR = ".atomic"
        private val KEY = createKey(NAME)

        fun getKey(): VcsKey = KEY

        fun getInstance(project: Project): AtomicVcs? = ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME) as? AtomicVcs
    }
}
