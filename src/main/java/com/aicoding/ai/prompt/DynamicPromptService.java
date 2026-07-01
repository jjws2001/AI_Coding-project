package com.aicoding.ai.prompt;

import com.aicoding.Entity.model.Project;
import com.aicoding.Service.ProjectService;
import com.aicoding.ai.harness.HarnessPolicyEngine;
import com.aicoding.ai.memory.LongTermMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class DynamicPromptService {

    private static final int MAX_PROJECT_INSTRUCTIONS = 8_000;
    private final PromptModuleLoader moduleLoader;
    private final AgentSkillService skillService;
    private final LongTermMemoryService memoryService;
    private final HarnessPolicyEngine policyEngine;
    private final ProjectService projectService;

    public String buildSystemPrompt(Long projectId) {
        Project project = projectService.getProjectById(projectId);
        return String.join("\n\n",
                moduleLoader.load("classpath:agent/prompts/identity.md"),
                moduleLoader.load("classpath:agent/prompts/tool-policy.md"),
                moduleLoader.load("classpath:agent/prompts/safety.md"),
                moduleLoader.load("classpath:agent/prompts/harness.md"),
                "## Project identity\nProject ID: " + projectId + "\nProject name: " + project.getName(),
                "## Available skills (metadata only)\n" + skillService.catalog(projectId),
                loadProjectInstructions(projectId));
    }

    public String enrichUserMessage(Long projectId, String sessionId, String userMessage) {
        String skills = skillService.matchingSkillContent(projectId, userMessage);
        String memories = memoryService.renderRecall(projectId, userMessage, 5);
        String workflow = policyEngine.classify(userMessage).asPrompt();

        StringBuilder context = new StringBuilder(userMessage.strip());
        context.append("\n\n<runtime-context>\n")
                .append("Project ID: ").append(projectId).append('\n')
                .append("Session ID: ").append(sessionId).append("\n\n")
                .append("### Harness route\n").append(workflow);
        if (!skills.isBlank()) {
            context.append("\n\n### Skills loaded on demand\n").append(skills);
        }
        if (!memories.isBlank()) {
            context.append("\n\n### Relevant long-term memory\n").append(memories);
        }
        context.append("\n</runtime-context>");
        return context.toString();
    }

    private String loadProjectInstructions(Long projectId) {
        try {
            Path root = projectService.getProjectRootPath(projectId);
            Path instructions = root.resolve(".aicoding/AGENT.md").normalize();
            if (!instructions.startsWith(root.normalize()) || !Files.isRegularFile(instructions) || Files.isSymbolicLink(instructions)) {
                return "## Project instructions\nNo project-specific AGENT.md file.";
            }
            String content = Files.readString(instructions, StandardCharsets.UTF_8);
            if (content.length() > MAX_PROJECT_INSTRUCTIONS) {
                content = content.substring(0, MAX_PROJECT_INSTRUCTIONS) + "\n[project instructions truncated]";
            }
            return "## Project instructions\n" + content;
        } catch (Exception e) {
            return "## Project instructions\nProject instructions unavailable.";
        }
    }
}
