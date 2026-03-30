package com.aicoding.ai.tools;

import com.aicoding.Entity.SandboxResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class SandboxExecutionTool {

    // TODO 沙箱 API 地址(https://github.com/alibaba/OpenSandbox/blob/main/server/README_zh.md)
    private static final String SANDBOX_API_URL = "http://localhost:8080" + "/sandboxes/<sandbox_id>/proxy/<execd_port>/code";

    // 重试配置
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * @Tool 注解会将这个方法的描述和参数精确地翻译成 JSON Schema 喂给 OpenAI。
     * 描述必须写得非常清晰，告诉模型什么时候用，以及参数的含义。
     */
    @Tool("Execute code in an isolated sandbox environment and get the console output or error messages. Use this to test your generated code.")
    public String executeCode(
            @P("The programming language of the code (e.g., 'python', 'java', 'typescript')") String language,
            @P("The complete, runnable source code string to be executed") String code) {

        log.info("Agent requested to execute {} code. Length: {}", language, code.length());

        // 核心逻辑：重试循环
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doExecuteInSandbox(language, code);
            } catch (TransientSandboxException e) {
                // 捕获瞬时错误（如网络波动、沙箱容器启动超时）
                log.warn("Sandbox execution transient error (Attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());

                if (attempt == MAX_RETRIES) {
                    // 告诉大模型沙箱崩了，不要让它以为是自己代码写错了
                    return "System Error: The sandbox environment is temporarily unavailable after " + MAX_RETRIES + " attempts. Please notify the user.";
                }

                // 等待后重试
                sleepQuietly(RETRY_DELAY_MS);
            } catch (CodeExecutionException e) {
                // 捕获代码自身的错误（语法错误、运行异常）。
                // ⚠️ 极其重要：这种错误绝对不能重试！必须立刻把报错日志原封不动返回给大模型，让它自己去 debug！
                log.info("Code execution failed due to syntax/runtime error. Returning error log to Agent.");
                return "Execution Failed with Error:\n" + e.getMessage();
            } catch (Exception e) {
                // 兜底的未知致命错误
                log.error("Fatal error during sandbox execution", e);
                return "System Error: Fatal exception occurred: " + e.getMessage();
            }
        }
        return "Unknown state.";
    }

    /**
     * 实际调用底层沙箱接口的方法
     */
    private String doExecuteInSandbox(String language, String code) {
        try {
            // 假设沙箱接收 JSON: {"language": "python", "code": "print('hello')"}
            Map<String, String> request = Map.of(
                    "language", language,
                    "code", code
            );

            // 发起 HTTP 请求到沙箱容器集群
            SandboxResponse response = restTemplate.postForObject(SANDBOX_API_URL, request, SandboxResponse.class);

            if (response == null) {
                throw new TransientSandboxException("Empty response from sandbox API");
            }

            // 业务逻辑判断：是代码执行错了，还是沙箱系统坏了？
            if ("SUCCESS".equals(response.getStatus())) {
                return "Execution Output:\n" + response.getOutput();
            } else if ("CODE_ERROR".equals(response.getStatus())) {
                // 模型写的代码有问题（比如拼写错误），抛出特定异常，不触发外层重试
                throw new CodeExecutionException(response.getErrorLog());
            } else {
                // 沙箱自身的问题（如 OOM、容器创建失败），触发重试
                throw new TransientSandboxException("Sandbox internal error: " + response.getStatus());
            }

        } catch (RestClientException e) {
            // 网络层面的超时、拒绝连接等，属于典型的瞬时错误，触发重试
            throw new TransientSandboxException("Network error connecting to sandbox: " + e.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- 自定义异常类 ---
    private static class TransientSandboxException extends RuntimeException {
        public TransientSandboxException(String message) { super(message); }
    }

    private static class CodeExecutionException extends RuntimeException {
        public CodeExecutionException(String message) { super(message); }
    }
}
