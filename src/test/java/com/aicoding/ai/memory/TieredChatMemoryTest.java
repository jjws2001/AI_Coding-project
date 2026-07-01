package com.aicoding.ai.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TieredChatMemoryTest {

    @Test
    void compactsOversizedToolOutputAndOldTurns() {
        TieredChatMemory memory = new TieredChatMemory("session", 6, 500, 1_200);
        memory.add(UserMessage.from("first request"));
        memory.add(AiMessage.from("first response"));
        memory.add(ToolExecutionResultMessage.from("tool-1", "readFile", "x".repeat(2_000)));
        memory.add(UserMessage.from("second request"));
        memory.add(AiMessage.from("second response"));
        memory.add(UserMessage.from("third request"));
        memory.add(AiMessage.from("third response"));

        assertThat(memory.messages()).hasSizeLessThanOrEqualTo(6);
        assertThat(memory.messages().toString()).contains("COMPACTED CONVERSATION");
        assertThat(memory.messages().toString()).contains("middle truncated");
        assertThat(memory.messages().toString()).contains("third response");
    }

    @Test
    void supportsExplicitCompaction() {
        TieredChatMemory memory = new TieredChatMemory("session", 20, 500, 1_200);
        for (int i = 0; i < 10; i++) {
            memory.add(UserMessage.from("request " + i));
            memory.add(AiMessage.from("response " + i));
        }

        memory.compactNow();

        assertThat(memory.messages().toString()).contains("COMPACTED CONVERSATION");
        assertThat(memory.messages().size()).isLessThan(10);
    }
}
