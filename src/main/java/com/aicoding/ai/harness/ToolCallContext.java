package com.aicoding.ai.harness;

public record ToolCallContext(
        Long projectId,
        String toolName,
        ToolAction action,
        String filePath,
        long payloadBytes
) {
    public static ToolCallContext of(Long projectId, String toolName, ToolAction action) {
        return new ToolCallContext(projectId, toolName, action, null, 0);
    }

    public static ToolCallContext file(Long projectId, String toolName, ToolAction action,
                                       String filePath, long payloadBytes) {
        return new ToolCallContext(projectId, toolName, action, filePath, payloadBytes);
    }
}
