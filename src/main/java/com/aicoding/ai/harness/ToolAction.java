package com.aicoding.ai.harness;

public enum ToolAction {
    FILE_READ(false),
    FILE_LIST(false),
    FILE_WRITE(true),
    FILE_CREATE(true),
    FILE_DELETE(true),
    GIT_READ(false),
    GIT_COMMIT(true),
    SANDBOX_EXECUTE(false),
    MEMORY_WRITE(false);

    private final boolean mutation;

    ToolAction(boolean mutation) {
        this.mutation = mutation;
    }

    public boolean isMutation() {
        return mutation;
    }
}
