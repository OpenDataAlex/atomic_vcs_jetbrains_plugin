package dev.atomic.vcs.rollback;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import dev.atomic.core.cli.AtomicCli;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class AtomicRollbackEnvironment extends DefaultRollbackEnvironment {

    private final Project project;
    private final AtomicCli cli;

    public AtomicRollbackEnvironment(Project project, AtomicCli cli) {
        this.project = project;
        this.cli = cli;
    }

    @Override
    public String getRollbackOperationName() {
        return "Restore";
    }

    @Override
    public void rollbackChanges(List<? extends Change> changes, List<VcsException> vcsExceptions, RollbackProgressListener listener) {
        List<Path> pathsToRestore = changes.stream()
                .map(AtomicRollbackEnvironment::pathOf)
                .filter(Objects::nonNull)
                .map(Path::of)
                .toList();
        restore(pathsToRestore, "Failed to restore files: ", vcsExceptions::add);
    }

    @Override
    public void rollbackMissingFileDeletion(List<? extends FilePath> files, List<? super VcsException> exceptions, RollbackProgressListener listener) {
        List<Path> pathsToRestore = files.stream()
                .map(file -> Path.of(file.getPath()))
                .toList();
        restore(pathsToRestore, "Failed to restore deleted files: ", exceptions::add);
    }

    private static String pathOf(Change change) {
        ContentRevision revision = change.getAfterRevision() != null
                ? change.getAfterRevision()
                : change.getBeforeRevision();
        return revision == null ? null : revision.getFile().getPath();
    }

    private void restore(List<Path> pathsToRestore, String errorPrefix, Consumer<VcsException> errors) {
        String basePath = project.getBasePath();
        if (basePath == null || pathsToRestore.isEmpty()) return;
        Path root = Path.of(basePath);

        try {
            BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (scope, continuation) ->
                cli.restore(root, pathsToRestore, continuation)
            );
            LocalFileSystem.getInstance().refresh(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.accept(new VcsException(errorPrefix + "interrupted", e));
        } catch (Exception e) {
            errors.accept(new VcsException(errorPrefix + e.getMessage(), e));
        }
    }
}
