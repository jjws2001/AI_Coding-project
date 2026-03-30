package com.aicoding.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.TokenStream;
import reactor.core.publisher.Flux;

/**
 * AI编程助手接口
 */
public interface AiCodingAssistant {

    /**
     * 同步对话
     */
    @SystemMessage("""
        You are an expert coding assistant helping developers write, debug, and improve code.
        You have access to the project files and can analyze code, suggest improvements, and help fix bugs.
        Always provide clear, concise, and actionable advice.
        When suggesting code changes, provide complete, runnable code snippets.
        """)
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);

    /**
     * 流式对话
     */
    @SystemMessage("""
        You are an expert coding assistant helping developers write, debug, and improve code.
        You have access to the project files and can analyze code, suggest improvements, and help fix bugs.
        Always provide clear, concise, and actionable advice.
        When suggesting code changes, provide complete, runnable code snippets.
        """)
    Flux<String> chatStream(@MemoryId String sessionId, @UserMessage String userMessage);

    /**
     * 代码审查
     */
    @SystemMessage("You are a code reviewer. Analyze the provided code and suggest improvements.")
    String reviewCode(@MemoryId String sessionId, @UserMessage String code);

    /**
     * 代码解释
     */
    @SystemMessage("You are a programming tutor. Explain the provided code in detail.")
    String explainCode(@UserMessage String code);

    /**
     * 生成代码
     */
    @SystemMessage("You are a code generator. Generate code based on the user's requirements.")
    String generateCode(@UserMessage @V("requirements") String requirements,
                        @V("language") String language);

    /**
     * 修复代码错误
     */
    @SystemMessage("You are a debugging expert. Analyze the error and fix the code.")
    String fixCode(@UserMessage @V("code") String code,
                   @V("error") String errorMessage);
}
