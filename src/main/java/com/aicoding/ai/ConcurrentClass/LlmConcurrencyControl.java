package com.aicoding.ai.ConcurrentClass;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 限制对 LLM API 的并发调用（信号量 + 看门狗）
 */
@Slf4j
@Component
public class LlmConcurrencyControl {

    private final int maxConcurrency;
    private final long requestTimeoutSeconds;

    private Semaphore semaphore;
    private final Map<String, ActiveRequest> activeRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "llm-watchdog");
        t.setDaemon(true);
        return t;
    });

    public LlmConcurrencyControl(
            @Value("${ai.llm.max-concurrency:3}") int maxConcurrency,
            @Value("${ai.llm.request-timeout:60}") long requestTimeoutSeconds
    ) {
        this.maxConcurrency = maxConcurrency;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    @PostConstruct
    public void init() {
        // Initialize Semaphore with fairness=true to serve requests in FIFO order
        this.semaphore = new Semaphore(maxConcurrency, true);

        // Start Watchdog: Check for stuck requests every 10 seconds
        watchdogExecutor.scheduleAtFixedRate(this::checkTimeouts, 10, 10, TimeUnit.SECONDS);
        log.info("LLM Concurrency Control initialized. Max concurrency: {}, Timeout: {}s", maxConcurrency, requestTimeoutSeconds);
    }

    @PreDestroy
    public void shutdown() {
        watchdogExecutor.shutdownNow();
    }

    /**
     * Try to acquire a permit.
     * @return true if acquired immediately, false if blocked
     */
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    /**
     * Register a new request monitoring
     */
    public void registerRequest(String requestId, Runnable cancelCallback) {
        activeRequests.put(requestId, new ActiveRequest(System.currentTimeMillis(), cancelCallback));
        log.debug("Registered request: {}, Active count: {}", requestId, activeRequests.size());
    }

    /**
     * Release permit and unregister monitoring
     */
    public void release(String requestId) {
        if (activeRequests.remove(requestId) != null) {
            semaphore.release();
            log.debug("Released request: {}, Available permits: {}", requestId, semaphore.availablePermits());
        }
    }

    /** Returns a permit acquired before a request could be registered. */
    public void releaseUnusedPermit() {
        semaphore.release();
    }

    /**
     * Watchdog logic: Force release permits for timed-out requests
     */
    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        long timeoutMillis = requestTimeoutSeconds * 1000;

        activeRequests.forEach((id, req) -> {
            if (now - req.startTime > timeoutMillis) {
                log.warn("Watchdog: Request {} timed out ({}ms). Forcing release.", id, now - req.startTime);
                if (!activeRequests.remove(id, req)) {
                    return;
                }
                semaphore.release();
                if (req.cancelCallback != null) {
                    try {
                        req.cancelCallback.run();
                    } catch (Exception e) {
                        log.error("Error executing cancel callback for {}", id, e);
                    }
                }
            }
        });
    }

    private record ActiveRequest(long startTime, Runnable cancelCallback) {}

    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    public int getQueueLength() {
        return semaphore.getQueueLength();
    }

    public int getActiveRequestCount() {
        return activeRequests.size();
    }
}
