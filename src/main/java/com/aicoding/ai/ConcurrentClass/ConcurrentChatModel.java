package com.aicoding.ai.ConcurrentClass;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streaming model decorator with a fair semaphore, expiring request queue,
 * retry queue with exponential backoff, circuit breaker, and bounded DLQ.
 */
@Slf4j
@Component
@Primary
public class ConcurrentChatModel implements StreamingChatModel {

    private static final int MAX_DLQ_SIZE = 1_000;

    private final StreamingChatModel delegate;
    private final LlmConcurrencyControl concurrencyControl;
    private final PriorityBlockingQueue<PendingRequest> requestQueue = new PriorityBlockingQueue<>();
    private final ConcurrentLinkedDeque<DeadLetter> deadLetters = new ConcurrentLinkedDeque<>();
    private final ScheduledExecutorService dispatcher;
    private final Object dispatchLock = new Object();
    private final int maxRetries;
    private final long retryBaseDelayMs;
    private final long retryMaxDelayMs;
    private final long queueTtlMs;
    private final int circuitFailureThreshold;
    private final long circuitOpenMs;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntil = new AtomicLong();

    public ConcurrentChatModel(
            @Qualifier("openAiStreamingChatModel") StreamingChatModel delegate,
            LlmConcurrencyControl concurrencyControl,
            @Value("${ai.llm.max-retries:3}") int maxRetries,
            @Value("${ai.llm.retry-base-delay-ms:500}") long retryBaseDelayMs,
            @Value("${ai.llm.retry-max-delay-ms:8000}") long retryMaxDelayMs,
            @Value("${ai.llm.queue-ttl-seconds:120}") long queueTtlSeconds,
            @Value("${ai.llm.circuit-failure-threshold:5}") int circuitFailureThreshold,
            @Value("${ai.llm.circuit-open-seconds:30}") long circuitOpenSeconds) {
        this.delegate = delegate;
        this.concurrencyControl = concurrencyControl;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBaseDelayMs = Math.max(10, retryBaseDelayMs);
        this.retryMaxDelayMs = Math.max(this.retryBaseDelayMs, retryMaxDelayMs);
        this.queueTtlMs = TimeUnit.SECONDS.toMillis(Math.max(1, queueTtlSeconds));
        this.circuitFailureThreshold = Math.max(1, circuitFailureThreshold);
        this.circuitOpenMs = TimeUnit.SECONDS.toMillis(Math.max(1, circuitOpenSeconds));
        this.dispatcher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "llm-request-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        this.dispatcher.scheduleWithFixedDelay(this::processQueueSafely, 0, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
        enqueue(ChatRequest.builder().messages(List.copyOf(messages)).build(), handler);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        enqueue(chatRequest, handler);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private void enqueue(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        long now = System.currentTimeMillis();
        PendingRequest request = new PendingRequest(UUID.randomUUID().toString(), chatRequest, handler,
                0, now, now);
        if (circuitOpenUntil.get() > now) {
            failPermanently(request, new RejectedExecutionException("LLM circuit breaker is open"));
            return;
        }
        requestQueue.offer(request);
        processQueueSafely();
    }

    private void processQueueSafely() {
        try {
            processQueue();
        } catch (RuntimeException e) {
            log.error("LLM queue dispatcher failed", e);
        }
    }

    private void processQueue() {
        synchronized (dispatchLock) {
            while (true) {
                PendingRequest next = requestQueue.peek();
                if (next == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (isExpired(next, now)) {
                    requestQueue.poll();
                    failPermanently(next, new TimeoutException("LLM request expired in queue"));
                    continue;
                }
                if (next.availableAtMs > now) {
                    return;
                }
                if (circuitOpenUntil.get() > now || !concurrencyControl.tryAcquire()) {
                    return;
                }
                PendingRequest request = requestQueue.poll();
                if (request == null) {
                    concurrencyControl.releaseUnusedPermit();
                    return;
                }
                startRequest(request);
            }
        }
    }

    private void startRequest(PendingRequest request) {
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean emitted = new AtomicBoolean(false);

        concurrencyControl.registerRequest(request.id, () -> {
            if (finished.compareAndSet(false, true)) {
                handleFailure(request, new TimeoutException("LLM request timed out"), emitted.get(), false);
            }
        });

        try {
            delegate.chat(request.chatRequest, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (!finished.get()) {
                        emitted.set(true);
                        request.handler.onPartialResponse(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    if (finished.compareAndSet(false, true)) {
                        consecutiveFailures.set(0);
                        concurrencyControl.release(request.id);
                        request.handler.onCompleteResponse(completeResponse);
                        processQueueSafely();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (finished.compareAndSet(false, true)) {
                        concurrencyControl.release(request.id);
                        handleFailure(request, error, emitted.get(), true);
                    }
                }
            });
        } catch (RuntimeException e) {
            if (finished.compareAndSet(false, true)) {
                concurrencyControl.release(request.id);
                handleFailure(request, e, emitted.get(), true);
            }
        }
    }

    private void handleFailure(PendingRequest request, Throwable error, boolean emitted, boolean processAfter) {
        long now = System.currentTimeMillis();
        if (!emitted && isRetryable(error) && request.attempt < maxRetries && !isExpired(request, now)) {
            long delay = Math.min(retryMaxDelayMs, retryBaseDelayMs * (1L << request.attempt));
            requestQueue.offer(request.retryAt(now + delay));
            log.warn("Retrying LLM request {} in {}ms (attempt {}/{})", request.id, delay,
                    request.attempt + 1, maxRetries);
        } else {
            failPermanently(request, error);
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= circuitFailureThreshold && isRetryable(error)) {
                circuitOpenUntil.set(now + circuitOpenMs);
                consecutiveFailures.set(0);
                log.error("LLM circuit breaker opened for {}ms", circuitOpenMs);
            }
        }
        if (processAfter) {
            processQueueSafely();
        }
    }

    private void failPermanently(PendingRequest request, Throwable error) {
        DeadLetter deadLetter = new DeadLetter(request.id, request.attempt, Instant.now(),
                error.getClass().getSimpleName(), String.valueOf(error.getMessage()));
        deadLetters.addFirst(deadLetter);
        while (deadLetters.size() > MAX_DLQ_SIZE) {
            deadLetters.pollLast();
        }
        try {
            request.handler.onError(error);
        } catch (RuntimeException handlerError) {
            log.warn("LLM error handler failed for request {}", request.id, handlerError);
        }
    }

    private boolean isRetryable(Throwable error) {
        if (error instanceof TimeoutException || error instanceof IOException) {
            return true;
        }
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("timeout") || message.contains("timed out")
                || message.contains("429") || message.contains("rate limit")
                || message.contains("502") || message.contains("503")
                || message.contains("connection") || message.contains("temporarily unavailable");
    }

    private boolean isExpired(PendingRequest request, long now) {
        return now - request.enqueuedAtMs > queueTtlMs;
    }

    public int getQueuedRequestCount() {
        return requestQueue.size();
    }

    public int getDeadLetterCount() {
        return deadLetters.size();
    }

    public List<DeadLetter> getRecentDeadLetters() {
        return deadLetters.stream().limit(20).toList();
    }

    public boolean isCircuitOpen() {
        return circuitOpenUntil.get() > System.currentTimeMillis();
    }

    @PreDestroy
    public void shutdown() {
        dispatcher.shutdownNow();
    }

    private record PendingRequest(String id, ChatRequest chatRequest,
                                  StreamingChatResponseHandler handler, int attempt,
                                  long enqueuedAtMs, long availableAtMs)
            implements Comparable<PendingRequest> {

        private PendingRequest retryAt(long availableAtMs) {
            return new PendingRequest(id, chatRequest, handler, attempt + 1, enqueuedAtMs, availableAtMs);
        }

        @Override
        public int compareTo(PendingRequest other) {
            int byAvailability = Long.compare(availableAtMs, other.availableAtMs);
            return byAvailability != 0 ? byAvailability : Long.compare(enqueuedAtMs, other.enqueuedAtMs);
        }
    }

    public record DeadLetter(String requestId, int attempts, Instant failedAt,
                             String errorType, String errorMessage) {}
}
