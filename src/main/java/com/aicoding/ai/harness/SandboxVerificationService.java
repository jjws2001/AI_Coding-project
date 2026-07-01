package com.aicoding.ai.harness;

import com.aicoding.Entity.SandboxResponse;
import com.aicoding.Service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SandboxVerificationService {

    private final SandboxGatewayClient sandboxClient;
    private final ProjectService projectService;

    public VerificationReport verify(Long projectId, String changedFile) {
        List<String> commands = commandsFor(projectId, changedFile);
        if (commands.isEmpty()) {
            return new VerificationReport(VerificationReport.Status.SKIPPED, List.of(),
                    "No code verification gate applies to this file type.");
        }
        if (!sandboxClient.isEnabled()) {
            return new VerificationReport(VerificationReport.Status.SKIPPED, commands,
                    "Sandbox gateway is disabled; no command was executed.");
        }

        try {
            Path workspace = projectService.getProjectRootPath(projectId);
            SandboxResponse response = sandboxClient.verify(projectId, workspace.toString(), changedFile, commands);
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                return new VerificationReport(VerificationReport.Status.PASS, commands, safe(response.getOutput()));
            }
            return new VerificationReport(VerificationReport.Status.FAIL, commands, safe(response.getErrorLog()));
        } catch (RuntimeException e) {
            return new VerificationReport(VerificationReport.Status.FAIL, commands,
                    "Sandbox verification failed: " + e.getMessage());
        }
    }

    private List<String> commandsFor(Long projectId, String changedFile) {
        if (changedFile == null) {
            return List.of();
        }
        String normalized = changedFile.replace('\\', '/').toLowerCase();
        Path root = projectService.getProjectRootPath(projectId);
        List<String> commands = new ArrayList<>();
        if (normalized.endsWith(".java") || normalized.endsWith("pom.xml")) {
            commands.add("mvn -q -DskipTests compile");
            commands.add("mvn -q test");
        } else if (normalized.endsWith(".ts") || normalized.endsWith(".tsx")
                || normalized.endsWith(".js") || normalized.endsWith(".jsx")) {
            String prefix = Files.exists(root.resolve("frontend/package.json")) ? "cd frontend && " : "";
            commands.add(prefix + "npm run build");
        } else if (normalized.endsWith(".py")) {
            commands.add("python -m compileall -q .");
        }
        return commands;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "No sandbox output" : value;
    }
}
