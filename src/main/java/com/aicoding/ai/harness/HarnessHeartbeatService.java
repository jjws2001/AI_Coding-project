package com.aicoding.ai.harness;

import com.aicoding.ai.ConcurrentClass.ConcurrentChatModel;
import com.aicoding.ai.ConcurrentClass.LlmConcurrencyControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HarnessHeartbeatService {

    private final HarnessRuntimeRegistry runtimeRegistry;
    private final ConcurrentChatModel concurrentChatModel;
    private final LlmConcurrencyControl concurrencyControl;

    @Value("${ai.harness.stale-run-seconds:180}")
    private long staleRunSeconds;

    private volatile HeartbeatReport latest = HeartbeatReport.starting();

    @Scheduled(fixedDelayString = "${ai.harness.heartbeat-interval-ms:15000}")
    public void evaluate() {
        HarnessRuntimeRegistry.RuntimeSnapshot runtime = runtimeRegistry.snapshot(Duration.ofSeconds(staleRunSeconds));
        int stabilityScore = clamp(100
                - (int) runtime.staleRuns() * 30
                - concurrentChatModel.getDeadLetterCount() * 5
                - (concurrentChatModel.isCircuitOpen() ? 40 : 0));
        int complianceScore = clamp(100
                - (int) runtime.violations() * 10
                - (int) runtime.verificationFailed() * 5);
        String state = stabilityScore < 60 || complianceScore < 60 ? "DEGRADED" : "HEALTHY";

        latest = new HeartbeatReport(Instant.now(), state, stabilityScore, complianceScore,
                runtime.activeRuns(), runtime.staleRuns(), runtime.violations(),
                runtime.verificationPassed(), runtime.verificationFailed(),
                concurrencyControl.getActiveRequestCount(), concurrencyControl.getAvailablePermits(),
                concurrentChatModel.getQueuedRequestCount(), concurrentChatModel.getDeadLetterCount(),
                concurrentChatModel.isCircuitOpen(), concurrentChatModel.getRecentDeadLetters(),
                runtime.recentEvents().stream().limit(30).toList());

        if (!"HEALTHY".equals(state)) {
            log.warn("Harness HEARTBEAT degraded: stability={}, compliance={}, staleRuns={}, dlq={}",
                    stabilityScore, complianceScore, runtime.staleRuns(), concurrentChatModel.getDeadLetterCount());
        } else {
            log.debug("Harness HEARTBEAT healthy: activeRuns={}, queuedLlm={}",
                    runtime.activeRuns(), concurrentChatModel.getQueuedRequestCount());
        }
    }

    public HeartbeatReport latest() {
        return latest;
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    public record HeartbeatReport(
            Instant evaluatedAt,
            String state,
            int stabilityScore,
            int complianceScore,
            int activeAgentRuns,
            long staleAgentRuns,
            long harnessViolations,
            long verificationPassed,
            long verificationFailed,
            int activeLlmRequests,
            int availableLlmPermits,
            int queuedLlmRequests,
            int deadLetterRequests,
            boolean circuitOpen,
            List<ConcurrentChatModel.DeadLetter> recentDeadLetters,
            List<HarnessRuntimeRegistry.AuditEvent> recentHarnessEvents) {

        private static HeartbeatReport starting() {
            return new HeartbeatReport(Instant.now(), "STARTING", 100, 100,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, false, List.of(), List.of());
        }
    }
}
