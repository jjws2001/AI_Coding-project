package com.aicoding.ai.harness;

import com.aicoding.Service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceGuardTest {

    @TempDir
    Path workspace;

    private ProjectService projectService;
    private WorkspaceGuard guard;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        guard = new WorkspaceGuard(projectService);
        ReflectionTestUtils.setField(guard, "maxWriteBytes", 1_024L);
        ReflectionTestUtils.setField(guard, "allowDelete", false);
        when(projectService.getProjectRootPath(1L)).thenReturn(workspace);
        when(projectService.getProjectFilePath(eq(1L), anyString()))
                .thenAnswer(invocation -> workspace.resolve(invocation.getArgument(1, String.class)).normalize());
    }

    @Test
    void rejectsMutationOfHarnessControlFiles() {
        ToolCallContext context = ToolCallContext.file(1L, "writeFile", ToolAction.FILE_WRITE,
                ".aicoding/AGENT.md", 10);

        assertThatThrownBy(() -> guard.check(context))
                .isInstanceOf(WorkspaceGuard.HarnessViolationException.class)
                .hasMessageContaining("control files");
    }

    @Test
    void rejectsWorkspaceEscapeEvenIfDownstreamResolverIsWrong() {
        ToolCallContext context = ToolCallContext.file(1L, "readFile", ToolAction.FILE_READ,
                "../outside.txt", 0);

        assertThatThrownBy(() -> guard.check(context))
                .isInstanceOf(WorkspaceGuard.HarnessViolationException.class)
                .hasMessageContaining("workspace root");
    }

    @Test
    void rejectsDeleteByDefault() {
        ToolCallContext context = ToolCallContext.file(1L, "deleteFile", ToolAction.FILE_DELETE,
                "src/Old.java", 0);

        assertThatThrownBy(() -> guard.check(context))
                .isInstanceOf(WorkspaceGuard.HarnessViolationException.class)
                .hasMessageContaining("deletion is disabled");
    }
}
