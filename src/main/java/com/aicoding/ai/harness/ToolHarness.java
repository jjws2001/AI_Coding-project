package com.aicoding.ai.harness;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class ToolHarness {

    private final WorkspaceGuard workspaceGuard;
    private final SandboxVerificationService verificationService;
    private final HarnessRuntimeRegistry runtimeRegistry;

    @Value("${ai.context.max-tool-result-chars:6000}")
    private int maxToolResultChars;

    public String execute(ToolCallContext context, Supplier<String> action) {
        try {
            if (context.action() == ToolAction.GIT_COMMIT && !runtimeRegistry.isCommitAllowed(context.projectId())) {
                throw new WorkspaceGuard.HarnessViolationException(
                        "Git commit requires the latest code verification gate to be PASS");
            }
            workspaceGuard.check(context);
            runtimeRegistry.recordTool(context.projectId(), context.toolName(), true, "before-hook passed");
        } catch (RuntimeException e) {
            runtimeRegistry.recordTool(context.projectId(), context.toolName(), false, e.getMessage());
            return "HARNESS_REJECTED: " + e.getMessage();
        }

        try {
            String result = compact(action.get());
            if (context.action() == ToolAction.FILE_WRITE
                    || context.action() == ToolAction.FILE_CREATE
                    || context.action() == ToolAction.FILE_DELETE) {
                VerificationReport report = verificationService.verify(context.projectId(), context.filePath());
                runtimeRegistry.recordVerification(context.projectId(), report);
                return result + "\n\n" + compact(report.asToolOutput());
            }
            return result;
        } catch (RuntimeException e) {
            runtimeRegistry.recordTool(context.projectId(), context.toolName(), false, e.getMessage());
            return "TOOL_FAILED: " + e.getMessage();
        }
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxToolResultChars) {
            return value;
        }
        int side = Math.max(1, (maxToolResultChars - 40) / 2);
        return value.substring(0, side) + "\n...[middle truncated by Harness]...\n"
                + value.substring(value.length() - side);
    }
}
