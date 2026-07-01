package com.aicoding.ai.memory;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * L1 trims oversized tool output, L2 compacts old turns into a deterministic
 * summary, and L3 exposes explicit compaction through ChatMemoryRegistry.
 */
public class TieredChatMemory implements ChatMemory {

    private static final String SUMMARY_PREFIX = "[COMPACTED CONVERSATION]\n";

    private final Object id;
    private final int maxMessages;
    private final int recentMessages;
    private final int maxToolResultChars;
    private final int maxSummaryChars;
    private final List<ChatMessage> messages = new ArrayList<>();

    public TieredChatMemory(Object id, int maxMessages, int maxToolResultChars, int maxSummaryChars) {
        this.id = Objects.requireNonNull(id, "id");
        this.maxMessages = Math.max(6, maxMessages);
        this.recentMessages = Math.max(4, this.maxMessages / 2);
        this.maxToolResultChars = Math.max(500, maxToolResultChars);
        this.maxSummaryChars = Math.max(1_000, maxSummaryChars);
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public synchronized void add(ChatMessage message) {
        ChatMessage compacted = microCompact(Objects.requireNonNull(message, "message"));
        if (compacted instanceof SystemMessage) {
            messages.removeIf(existing -> existing instanceof SystemMessage);
            messages.add(0, compacted);
        } else {
            messages.add(compacted);
        }
        if (messages.size() > maxMessages) {
            compact(recentMessages);
        }
    }

    @Override
    public synchronized List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    @Override
    public synchronized void clear() {
        messages.clear();
    }

    public synchronized void compactNow() {
        compact(4);
    }

    private ChatMessage microCompact(ChatMessage message) {
        if (message instanceof ToolExecutionResultMessage result && result.text().length() > maxToolResultChars) {
            String compacted = headAndTail(result.text(), maxToolResultChars);
            return ToolExecutionResultMessage.from(result.id(), result.toolName(), compacted);
        }
        return message;
    }

    private void compact(int keepRecent) {
        List<ChatMessage> systemMessages = messages.stream()
                .filter(SystemMessage.class::isInstance)
                .toList();
        List<ChatMessage> conversation = messages.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .toList();
        if (conversation.size() <= keepRecent) {
            return;
        }

        int cut = conversation.size() - keepRecent;
        while (cut > 0 && conversation.get(cut) instanceof ToolExecutionResultMessage) {
            cut--;
        }
        List<ChatMessage> oldMessages = conversation.subList(0, cut);
        List<ChatMessage> recent = conversation.subList(cut, conversation.size());

        StringBuilder summary = new StringBuilder(SUMMARY_PREFIX);
        for (ChatMessage old : oldMessages) {
            String text = messageText(old);
            if (text.isBlank() || text.startsWith(SUMMARY_PREFIX)) {
                continue;
            }
            summary.append(old.type()).append(": ").append(headAndTail(text, 500)).append('\n');
            if (summary.length() >= maxSummaryChars) {
                break;
            }
        }

        messages.clear();
        if (!systemMessages.isEmpty()) {
            messages.add(systemMessages.get(systemMessages.size() - 1));
        }
        messages.add(UserMessage.from(headAndTail(summary.toString(), maxSummaryChars)));
        messages.addAll(recent);
    }

    private String messageText(ChatMessage message) {
        if (message instanceof UserMessage user && user.hasSingleText()) {
            return user.singleText();
        }
        if (message instanceof AiMessage ai) {
            return ai.text() == null ? "tool requests: " + ai.toolExecutionRequests() : ai.text();
        }
        if (message instanceof ToolExecutionResultMessage tool) {
            return tool.toolName() + ": " + tool.text();
        }
        if (message instanceof SystemMessage system) {
            return system.text();
        }
        return message.toString();
    }

    private String headAndTail(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        int side = Math.max(1, (maxChars - 40) / 2);
        return value.substring(0, side) + "\n...[middle truncated]...\n" + value.substring(value.length() - side);
    }
}
