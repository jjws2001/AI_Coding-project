package com.aicoding.ai.harness;

import com.aicoding.Entity.SandboxResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SandboxGatewayClient {

    private final RestTemplate restTemplate;

    @Value("${ai.sandbox.enabled:false}")
    private boolean enabled;

    @Value("${ai.sandbox.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${ai.sandbox.execute-path:/api/sandbox/execute}")
    private String executePath;

    @Value("${ai.sandbox.verify-path:/api/sandbox/verify}")
    private String verifyPath;

    @Value("${ai.sandbox.max-retries:3}")
    private int maxRetries;

    @Value("${ai.sandbox.retry-delay-ms:500}")
    private long retryDelayMs;

    public SandboxGatewayClient(RestTemplateBuilder builder,
                                @Value("${ai.sandbox.timeout-seconds:120}") long timeoutSeconds) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SandboxResponse executeSnippet(Long projectId, String language, String code) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", projectId);
        request.put("language", language);
        request.put("code", code);
        return postWithRetry(executePath, request);
    }

    public SandboxResponse verify(Long projectId, String workspace, String changedFile, List<String> commands) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", projectId);
        request.put("workspace", workspace);
        request.put("changedFile", changedFile);
        request.put("commands", commands);
        request.put("networkDisabled", true);
        return postWithRetry(verifyPath, request);
    }

    private SandboxResponse postWithRetry(String path, Map<String, Object> request) {
        RuntimeException lastError = null;
        int attempts = Math.max(1, maxRetries);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                SandboxResponse response = restTemplate.postForObject(baseUrl + path, request, SandboxResponse.class);
                if (response == null) {
                    throw new IllegalStateException("Sandbox returned an empty response");
                }
                return response;
            } catch (RestClientException | IllegalStateException e) {
                lastError = new IllegalStateException("Sandbox gateway unavailable: " + e.getMessage(), e);
                if (attempt < attempts) {
                    sleep(Math.min(5_000, retryDelayMs * (1L << (attempt - 1))));
                }
            }
        }
        throw lastError == null ? new IllegalStateException("Sandbox request failed") : lastError;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sandbox retry interrupted", e);
        }
    }
}
