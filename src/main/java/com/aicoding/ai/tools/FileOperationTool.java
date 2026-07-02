package com.aicoding.ai.tools;

import com.aicoding.Service.ProjectService;
import com.aicoding.ai.harness.ToolAction;
import com.aicoding.ai.harness.ToolCallContext;
import com.aicoding.ai.harness.ToolHarness;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileOperationTool {

    private final ProjectService projectService;
    private final ToolHarness toolHarness;

    @Tool("Read the content of a file in the project")
    public String readFile(Long projectId, String filePath) {
        log.info("Tool: Reading file {} from project {}", filePath, projectId);
        return toolHarness.execute(
                ToolCallContext.file(projectId, "readFile", ToolAction.FILE_READ, filePath, 0),
                () -> projectService.getFileContent(projectId, filePath));
    }

    @Tool("Write content to a file in the project")
    public String writeFile(Long projectId, String filePath, String content) {
        log.info("Tool: Writing to file {} in project {}", filePath, projectId);
        return toolHarness.execute(
                ToolCallContext.file(projectId, "writeFile", ToolAction.FILE_WRITE, filePath, bytes(content)),
                () -> {
                    projectService.updateFileContent(projectId, filePath, content);
                    return "File written successfully";
                });
    }

    @Tool("Create a new file in the project")
    public String createFile(Long projectId, String filePath, String content) {
        log.info("Tool: Creating file {} in project {}", filePath, projectId);
        return toolHarness.execute(
                ToolCallContext.file(projectId, "createFile", ToolAction.FILE_CREATE, filePath, bytes(content)),
                () -> {
                    projectService.createFile(projectId, filePath, content);
                    return "File created successfully";
                });
    }

    @Tool("Delete a file from the project")
    public String deleteFile(Long projectId, String filePath) {
        log.info("Tool: Deleting file {} from project {}", filePath, projectId);
        return toolHarness.execute(
                ToolCallContext.file(projectId, "deleteFile", ToolAction.FILE_DELETE, filePath, 0),
                () -> {
                    projectService.deleteFile(projectId, filePath);
                    return "File deleted successfully";
                });
    }

    @Tool("List all files in the project")
    public String listFiles(Long projectId) {
        log.info("Tool: Listing files in project {}", projectId);
        return toolHarness.execute(
                ToolCallContext.of(projectId, "listFiles", ToolAction.FILE_LIST),
                () -> projectService.getProjectFileTree(projectId).toString());
    }

    private long bytes(String content) {
        return content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
    }
}
