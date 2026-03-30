package com.aicoding.ai.guardrail;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GuardrailsFilter {

    @Value("#{'${guardrails.patterns}'.split(',')}")
    private List<String> sensitivePatterns;

    // 缓存预编译好的正则对象
    private final List<Pattern> compiledPatterns = new ArrayList<>();

    // 在 Spring Bean 初始化时，把所有正则只编译一次
    @PostConstruct
    public void init() {
        for (String patternStr : sensitivePatterns) {
            compiledPatterns.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE));
        }
    }

    /**
     * 过滤敏感信息
     */
    // 实际过滤时，直接使用预编译好的对象
    public String filter(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String filtered = content;
        for (Pattern pattern : compiledPatterns) {
            filtered = pattern.matcher(filtered).replaceAll("[REDACTED]");
        }

        return filtered;
    }

    /**
     * 检查内容是否包含敏感信息
     */
    public boolean containsSensitiveInfo(String content) {
        if (content == null) {
            return false;
        }

        for (String patternStr : sensitivePatterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(content).find()) {
                log.warn("Sensitive information detected in content");
                return true;
            }
        }

        return false;
    }
}
