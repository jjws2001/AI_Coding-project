package com.aicoding.ai.tools;

import com.aicoding.Service.ProjectService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileOperationTool {

    private final ProjectService projectService;

    @Tool("Read the content of a file in the project")
    public String readFile(Long projectId, String filePath) {
        log.info("Tool: Reading file {} from project {}", filePath, projectId);
        try {
            return projectService.getFileContent(projectId, filePath);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("Write content to a file in the project")
    public String writeFile(Long projectId, String filePath, String content) {
        log.info("Tool: Writing to file {} in project {}", filePath, projectId);
        try {
            projectService.updateFileContent(projectId, filePath, content);
            return "File written successfully";
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool("Create a new file in the project")
    public String createFile(Long projectId, String filePath, String content) {
        log.info("Tool: Creating file {} in project {}", filePath, projectId);
        try {
            projectService.createFile(projectId, filePath, content);
            return "File created successfully";
        } catch (Exception e) {
            return "Error creating file: " + e.getMessage();
        }
    }

    @Tool("Delete a file from the project")
    public String deleteFile(Long projectId, String filePath) {
        log.info("Tool: Deleting file {} from project {}", filePath, projectId);
        try {
            projectService.deleteFile(projectId, filePath);
            return "File deleted successfully";
        } catch (Exception e) {
            return "Error deleting file: " + e.getMessage();
        }
    }

    @Tool("List all files in the project")
    public String listFiles(Long projectId) {
        log.info("Tool: Listing files in project {}", projectId);
        try {
            var fileTree = projectService.getProjectFileTree(projectId);
            return fileTree.toString();
        } catch (Exception e) {
            return "Error listing files: " + e.getMessage();
        }
    }
}
