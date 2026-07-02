package com.aicoding.ai.tools;

import com.aicoding.Entity.SandboxResponse;
import com.aicoding.ai.harness.SandboxGatewayClient;
import com.aicoding.ai.harness.ToolAction;
import com.aicoding.ai.harness.ToolCallContext;
import com.aicoding.ai.harness.ToolHarness;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SandboxExecutionTool {

    private final SandboxGatewayClient sandboxClient;
    private final ToolHarness toolHarness;

    @Tool("Execute generated code in the isolated OpenSandbox gateway. Use it for runtime evidence; never execute generated code on the host.")
    public String executeCode(
            @P("Project id from runtime context") Long projectId,
            @P("Programming language, for example python, java, or typescript") String language,
            @P("Complete runnable source code") String code) {
        return toolHarness.execute(
                ToolCallContext.of(projectId, "executeCode", ToolAction.SANDBOX_EXECUTE),
                () -> execute(projectId, language, code));
    }

    private String execute(Long projectId, String language, String code) {
        if (!sandboxClient.isEnabled()) {
            return "Sandbox execution skipped: the OpenSandbox gateway is disabled.";
        }
        SandboxResponse response = sandboxClient.executeSnippet(projectId, language, code);
        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
            return "Execution output:\n" + safe(response.getOutput());
        }
        if ("CODE_ERROR".equalsIgnoreCase(response.getStatus())) {
            return "Execution failed because of generated code:\n" + safe(response.getErrorLog());
        }
        throw new IllegalStateException("Sandbox system error: " + response.getStatus() + "\n" + safe(response.getErrorLog()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
