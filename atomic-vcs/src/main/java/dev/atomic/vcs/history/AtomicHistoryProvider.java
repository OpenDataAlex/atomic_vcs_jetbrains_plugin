package dev.atomic.vcs.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import dev.atomic.core.cli.AtomicCli;
import dev.atomic.core.model.AtomicChangeSummary;
import dev.atomic.vcs.AtomicRevisionNumber;
import com.intellij.openapi.diagnostic.Logger;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.nio.file.Path;
import java.util.List;

public class AtomicHistoryProvider implements VcsHistoryProvider {

    private static final Logger LOG = Logger.getInstance(AtomicHistoryProvider.class);

    private final Project project;
    private final AtomicCli cli;

    public AtomicHistoryProvider(Project project, AtomicCli cli) {
        this.project = project;
        this.cli = cli;
    }

    @Override
    public boolean canShowHistoryFor(@NotNull VirtualFile file) {
        return true;
    }

    @Nullable
    @Override
    public VcsDependentHistoryComponents getUICustomization(@NotNull VcsHistorySession session, @NotNull JComponent forComponent) {
        return null;
    }

    @Nullable
    @Override
    public DiffFromHistoryHandler getHistoryDiffHandler() {
        return null;
    }

    @Nullable
    @Override
    public VcsHistorySession createSessionFor(FilePath filePath) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        Path root = Path.of(basePath);
        Path targetPath = Path.of(filePath.getPath());

        try {
            List<AtomicChangeSummary> logs = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (scope, continuation) ->
                cli.log(root, targetPath, 100, continuation)
            );
            List<VcsFileRevision> revisions = logs.stream()
                    .<VcsFileRevision>map(summary -> new AtomicFileRevision(filePath, summary, root, cli))
                    .toList();
            return new VcsAbstractHistorySession(revisions) {
                @Override
                public boolean isContentAvailable(VcsFileRevision revision) {
                    return true;
                }

                @Nullable
                @Override
                protected VcsRevisionNumber calcCurrentRevisionNumber() {
                    return revisions.isEmpty() ? AtomicRevisionNumber.Companion.getHEAD() : revisions.get(0).getRevisionNumber();
                }

                @Override
                public VcsHistorySession copy() {
                    return this;
                }
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Interrupted while reading Atomic history for " + filePath.getPath(), e);
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to read Atomic history for " + filePath.getPath(), e);
            return null;
        }
    }

    @Override
    public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) {
        VcsHistorySession session = createSessionFor(path);
        if (session != null) {
            for (VcsFileRevision rev : session.getRevisionList()) {
                partner.acceptRevision(rev);
            }
        }
    }

    @Override
    public boolean supportsHistoryForDirectories() {
        return true;
    }

    @Nullable
    @Override
    public String getHelpId() {
        return null;
    }

    @Override
    public boolean isDateOmittable() {
        return false;
    }

    @NotNull
    @Override
    public AnAction[] getAdditionalActions(Runnable callBack) {
        return new AnAction[0];
    }
}
