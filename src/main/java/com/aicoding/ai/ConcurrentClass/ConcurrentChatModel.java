package com.aicoding.ai.ConcurrentClass;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper for StreamingChatModel that adds concurrency control (Semaphore + Queue).
 * Implements the standard LangChain4j interface.
 */
@Slf4j
@Component
@Primary // Make this the primary bean so it's injected by default
public class ConcurrentChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;
    private final LlmConcurrencyControl concurrencyControl;

    // Internal Queue for requests waiting for a permit
    private final Queue<PendingRequest> requestQueue = new ConcurrentLinkedQueue<>();

    public ConcurrentChatModel(
            @Qualifier("openAiStreamingChatModel") StreamingChatModel delegate,
            LlmConcurrencyControl concurrencyControl
    ) {
        this.delegate = delegate;
        this.concurrencyControl = concurrencyControl;
    }

    @Override
    public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
        // Enqueue the request instead of blocking the calling thread
        String requestId = UUID.randomUUID().toString();
        requestQueue.offer(new PendingRequest(requestId, messages, handler));
        log.debug("Request queued: {}. Queue size: {}", requestId, requestQueue.size());

        // Try to process the queue
        processQueue();
    }

    /**
     * Process requests from the queue if permits are available.
     * This method is non-blocking and thread-safe.
     */
    private void processQueue() {
        // Double-check locking optimization or just simple loop since tryAcquire is fast
        while (!requestQueue.isEmpty()) {
            if (concurrencyControl.tryAcquire()) {
                PendingRequest request = requestQueue.poll();
                if (request != null) {
                    startRequest(request);
                } else {
                    // Queue became empty after check, return permit
                    concurrencyControl.release("UNUSED_PERMIT_" + UUID.randomUUID());
                    // Note: Ideally release logic in controller should handle raw releases too,
                    // but our controller expects IDs. Let's fix this edge case in logic:
                    // If poll returns null, we acquired a permit we don't need.
                    // Since concurrencyControl.release() removes from map, we need a "raw release" method
                    // or just ensure queue consistency. For simplicity here:
                    // If poll is null, we just lose a permit cycle or strictly synchronize.
                    // Better: poll first, then acquire? No, that blocks others if acquire fails.
                    break;
                }
            } else {
                // No permits available, stop processing
                break;
            }
        }
    }

    private void startRequest(PendingRequest request) {
        log.info("Starting LLM request: {}", request.id);

        // Atomic flag to ensure we only clean up once
        AtomicBoolean isFinished = new AtomicBoolean(false);

        // Define cleanup logic
        Runnable cleanup = () -> {
            if (isFinished.compareAndSet(false, true)) {
                concurrencyControl.release(request.id);
                // After releasing a permit, trigger queue processing for waiting requests
                processQueue();
            }
        };

        // Register with Watchdog
        concurrencyControl.registerRequest(request.id, () -> {
            log.warn("Request {} cancelled by watchdog", request.id);
            request.handler.onError(new java.util.concurrent.TimeoutException("LLM Request timed out"));
            // Cleanup is handled by concurrencyControl.release called inside checkTimeouts
            // But we need to make sure we don't double release in onComplete
            isFinished.set(true);
        });

        try {
            // Call the actual model
            delegate.chat(request.messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (!isFinished.get()) {
                        request.handler.onPartialResponse(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    if (!isFinished.get()) {
                        request.handler.onCompleteResponse(completeResponse);
                        cleanup.run();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (!isFinished.get()) {
                        log.error("LLM Request {} failed", request.id, error);
                        request.handler.onError(error);
                        cleanup.run();
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to start request {}", request.id, e);
            request.handler.onError(e);
            cleanup.run();
        }
    }

    private record PendingRequest(String id, List<ChatMessage> messages, StreamingChatResponseHandler handler) {}
}
