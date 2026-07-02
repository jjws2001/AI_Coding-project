package com.aicoding.ai.tools;

import com.aicoding.Git.GitService;
import com.aicoding.Service.ProjectService;
import com.aicoding.ai.harness.ToolAction;
import com.aicoding.ai.harness.ToolCallContext;
import com.aicoding.ai.harness.ToolHarness;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitOperationTool {

    private final GitService gitService;
    private final ProjectService projectService;
    private final ToolHarness toolHarness;


    @Tool("Check if project has uncommitted changes")
    public String checkGitStatus(Long projectId) {
        log.info("Tool: Checking git status for project {}", projectId);
        return toolHarness.execute(ToolCallContext.of(projectId, "checkGitStatus", ToolAction.GIT_READ), () -> {
            try {
            var project = projectService.getProjectById(projectId);
            boolean hasChanges = gitService.hasUncommittedChanges(project);
            return hasChanges ? "Project has uncommitted changes" : "Working tree is clean";
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        });
    }

    @Tool("Commit project changes with a message")
    public String commitChanges(Long projectId, String commitMessage) {
        log.info("Tool: Committing changes for project {}", projectId);
        return toolHarness.execute(ToolCallContext.of(projectId, "commitChanges", ToolAction.GIT_COMMIT), () -> {
            projectService.autoCommitChanges(projectId, commitMessage);
            return "Changes committed successfully";
        });
    }

    @Tool("Get current git branch name")
    public String getCurrentBranch(Long projectId) {
        log.info("Tool: Getting current branch for project {}", projectId);
        return toolHarness.execute(ToolCallContext.of(projectId, "getCurrentBranch", ToolAction.GIT_READ), () -> {
            try {
            var project = projectService.getProjectById(projectId);
            return "Current branch: " + gitService.getCurrentBranch(project);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        });
    }
}
