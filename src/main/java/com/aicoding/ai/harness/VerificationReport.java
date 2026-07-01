package com.aicoding.ai.harness;

import java.util.List;

public record VerificationReport(Status status, List<String> commands, String output) {

    public enum Status { PASS, FAIL, SKIPPED }

    public String asToolOutput() {
        String commandText = commands == null || commands.isEmpty() ? "none" : String.join("; ", commands);
        return "Hook verification: " + status + "\nCommands: " + commandText + "\n" + output;
    }
}
