package com.aicoding.ai.tools;

import com.aicoding.Git.GitService;
import com.aicoding.Service.ProjectService;
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


    @Tool("Check if project has uncommitted changes")
    public String checkGitStatus(Long projectId) {
        log.info("Tool: Checking git status for project {}", projectId);
        try {
            var project = projectService.getProjectById(projectId);
            boolean hasChanges = gitService.hasUncommittedChanges(project);
            return hasChanges ? "Project has uncommitted changes" : "Working tree is clean";
        } catch (Exception e) {
            return "Error checking git status: " + e.getMessage();
        }
    }

    @Tool("Commit project changes with a message")
    public String commitChanges(Long projectId, String commitMessage) {
        log.info("Tool: Committing changes for project {}", projectId);
        try {
            projectService.autoCommitChanges(projectId, commitMessage);
            return "Changes committed successfully";
        } catch (Exception e) {
            return "Error committing changes: " + e.getMessage();
        }
    }

    @Tool("Get current git branch name")
    public String getCurrentBranch(Long projectId) {
        log.info("Tool: Getting current branch for project {}", projectId);
        try {
            var project = projectService.getProjectById(projectId);
            return "Current branch: " + gitService.getCurrentBranch(project);
        } catch (Exception e) {
            return "Error getting branch: " + e.getMessage();
        }
    }
}
