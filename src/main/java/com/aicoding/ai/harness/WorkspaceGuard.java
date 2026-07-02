package com.aicoding.ai.harness;

import com.aicoding.Service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkspaceGuard {

    private static final Set<String> PROTECTED_ROOTS = Set.of(".git", ".aicoding");
    private final ProjectService projectService;

    @Value("${ai.harness.max-write-bytes:1048576}")
    private long maxWriteBytes;

    @Value("${ai.harness.allow-delete:false}")
    private boolean allowDelete;

    public void check(ToolCallContext context) {
        if (context.projectId() == null) {
            throw new HarnessViolationException("projectId is required");
        }
        if (context.action() == ToolAction.FILE_DELETE && !allowDelete) {
            throw new HarnessViolationException("File deletion is disabled by the fail-closed Harness policy");
        }
        if ((context.action() == ToolAction.FILE_WRITE || context.action() == ToolAction.FILE_CREATE)
                && context.payloadBytes() > maxWriteBytes) {
            throw new HarnessViolationException("Write payload exceeds " + maxWriteBytes + " bytes");
        }
        if (context.filePath() == null) {
            return;
        }

        Path supplied = Paths.get(context.filePath());
        if (supplied.isAbsolute()) {
            throw new HarnessViolationException("Absolute paths are not allowed");
        }

        Path root = projectService.getProjectRootPath(context.projectId()).toAbsolutePath().normalize();
        Path target = projectService.getProjectFilePath(context.projectId(), context.filePath());
        if (!target.startsWith(root) || target.equals(root) && context.action().isMutation()) {
            throw new HarnessViolationException("Tool call escapes or mutates the workspace root");
        }

        Path relative = root.relativize(target);
        if (context.action().isMutation() && relative.getNameCount() > 0
                && PROTECTED_ROOTS.contains(relative.getName(0).toString())) {
            throw new HarnessViolationException("Agent tools cannot mutate Harness or Git control files");
        }
        rejectSymbolicLinkEscape(root, target);
    }

    private void rejectSymbolicLinkEscape(Path root, Path target) {
        try {
            Path realRoot = Files.exists(root) ? root.toRealPath() : root;
            Path cursor = target;
            while (cursor != null && !Files.exists(cursor)) {
                cursor = cursor.getParent();
            }
            if (cursor != null) {
                Path realExistingPath = cursor.toRealPath();
                if (!realExistingPath.startsWith(realRoot)) {
                    throw new HarnessViolationException("Symbolic link resolves outside the workspace");
                }
            }
            if (Files.isSymbolicLink(target)) {
                throw new HarnessViolationException("Direct symbolic-link access is not allowed");
            }
        } catch (IOException e) {
            throw new HarnessViolationException("Workspace path could not be verified: " + e.getMessage());
        }
    }

    public static class HarnessViolationException extends RuntimeException {
        public HarnessViolationException(String message) {
            super(message);
        }
    }
}
