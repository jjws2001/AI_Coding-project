package com.aicoding.ai.harness;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class HarnessRuntimeRegistry {

    private static final int MAX_AUDIT_EVENTS = 500;
    private final Map<String, MutableRun> activeRuns = new ConcurrentHashMap<>();
    private final Deque<AuditEvent> auditEvents = new ConcurrentLinkedDeque<>();
    private final Map<Long, VerificationReport.Status> latestVerification = new ConcurrentHashMap<>();
    private final AtomicLong violations = new AtomicLong();
    private final AtomicLong verificationPassed = new AtomicLong();
    private final AtomicLong verificationFailed = new AtomicLong();

    public String begin(Long projectId, String sessionId, HarnessPolicyEngine.WorkflowPolicy policy) {
        String runId = UUID.randomUUID().toString();
        activeRuns.put(runId, new MutableRun(runId, projectId, sessionId, policy, Instant.now()));
        addAudit(new AuditEvent(Instant.now(), runId, projectId, "RUN_STARTED", true, policy.asPrompt()));
        return runId;
    }

    public void complete(String runId, boolean success, String detail) {
        MutableRun run = activeRuns.remove(runId);
        if (run != null) {
            addAudit(new AuditEvent(Instant.now(), runId, run.projectId, "RUN_COMPLETED", success, detail));
        }
    }

    public void recordTool(Long projectId, String toolName, boolean allowed, String detail) {
        if (!allowed) {
            violations.incrementAndGet();
        }
        activeRuns.values().stream()
                .filter(run -> Objects.equals(run.projectId, projectId))
                .max(Comparator.comparing(run -> run.lastActivity))
                .ifPresent(run -> run.lastActivity = Instant.now());
        addAudit(new AuditEvent(Instant.now(), null, projectId, "TOOL_" + toolName, allowed, detail));
    }

    public void recordVerification(Long projectId, VerificationReport report) {
        latestVerification.put(projectId, report.status());
        if (report.status() == VerificationReport.Status.PASS) {
            verificationPassed.incrementAndGet();
        } else if (report.status() == VerificationReport.Status.FAIL) {
            verificationFailed.incrementAndGet();
        }
        addAudit(new AuditEvent(Instant.now(), null, projectId, "VERIFICATION",
                report.status() == VerificationReport.Status.PASS, report.asToolOutput()));
    }

    public boolean isCommitAllowed(Long projectId) {
        return latestVerification.get(projectId) == VerificationReport.Status.PASS;
    }

    public RuntimeSnapshot snapshot(Duration staleAfter) {
        Instant now = Instant.now();
        long stale = activeRuns.values().stream()
                .filter(run -> Duration.between(run.lastActivity, now).compareTo(staleAfter) > 0)
                .count();
        return new RuntimeSnapshot(activeRuns.size(), stale, violations.get(),
                verificationPassed.get(), verificationFailed.get(), List.copyOf(auditEvents));
    }

    private void addAudit(AuditEvent event) {
        auditEvents.addFirst(event);
        while (auditEvents.size() > MAX_AUDIT_EVENTS) {
            auditEvents.pollLast();
        }
    }

    private static class MutableRun {
        private final String runId;
        private final Long projectId;
        private final String sessionId;
        private final HarnessPolicyEngine.WorkflowPolicy policy;
        private final Instant startedAt;
        private volatile Instant lastActivity;

        private MutableRun(String runId, Long projectId, String sessionId,
                           HarnessPolicyEngine.WorkflowPolicy policy, Instant startedAt) {
            this.runId = runId;
            this.projectId = projectId;
            this.sessionId = sessionId;
            this.policy = policy;
            this.startedAt = startedAt;
            this.lastActivity = startedAt;
        }
    }

    public record AuditEvent(Instant timestamp, String runId, Long projectId,
                             String event, boolean success, String detail) {}

    public record RuntimeSnapshot(int activeRuns, long staleRuns, long violations,
                                  long verificationPassed, long verificationFailed,
                                  List<AuditEvent> recentEvents) {}
}
