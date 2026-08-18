package com.aicoding.ai.ConcurrentClass;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentChatModelTest {

    private LlmConcurrencyControl concurrencyControl;
    private ConcurrentChatModel model;

    @AfterEach
    void tearDown() {
        if (model != null) model.shutdown();
        if (concurrencyControl != null) concurrencyControl.shutdown();
    }

    @Test
    void retriesTransientFailureBeforeAnyTokenWasEmitted() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        StreamingChatModel delegate = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                if (calls.incrementAndGet() == 1) {
                    handler.onError(new TimeoutException("temporary timeout"));
                } else {
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
                }
            }
        };
        concurrencyControl = new LlmConcurrencyControl(1, 10);
        concurrencyControl.init();
        model = new ConcurrentChatModel(delegate, concurrencyControl, 2, 10, 100,
                10, 5, 1);

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        model.chat(List.of(UserMessage.from("hello")), new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String partialResponse) {}
            @Override public void onCompleteResponse(ChatResponse completeResponse) { completed.countDown(); }
            @Override public void onError(Throwable throwable) { error.set(throwable); completed.countDown(); }
        });

        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(model.getDeadLetterCount()).isZero();
        assertThat(concurrencyControl.getAvailablePermits()).isEqualTo(1);
    }
}
