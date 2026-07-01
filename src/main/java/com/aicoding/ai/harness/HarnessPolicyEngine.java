package com.aicoding.ai.harness;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class HarnessPolicyEngine {

    public WorkflowPolicy classify(String request) {
        String normalized = request == null ? "" : request.toLowerCase(Locale.ROOT);
        Intent intent = detectIntent(normalized);
        Risk risk = detectRisk(normalized, intent);
        List<String> gates = switch (intent) {
            case QUERY -> List.of("workspace-boundary", "read-only");
            case CODE_REVIEW -> List.of("workspace-boundary", "evidence");
            case BUG_FIX -> risk == Risk.HIGH
                    ? List.of("workspace-boundary", "plan", "syntax", "compile", "unit-test", "evidence")
                    : List.of("workspace-boundary", "syntax", "compile", "unit-test");
            case FEATURE -> risk == Risk.HIGH
                    ? List.of("workspace-boundary", "plan", "syntax", "compile", "unit-test", "integration-test", "evidence")
                    : List.of("workspace-boundary", "plan", "syntax", "compile", "unit-test");
        };
        return new WorkflowPolicy(intent, risk, gates);
    }

    private Intent detectIntent(String text) {
        if (containsAny(text, "review", "审查", "评审", "检查代码")) {
            return Intent.CODE_REVIEW;
        }
        if (containsAny(text, "bug", "fix", "修复", "报错", "异常", "排错")) {
            return Intent.BUG_FIX;
        }
        if (containsAny(text, "实现", "新增", "开发", "重构", "modify", "create", "feature", "修改")) {
            return Intent.FEATURE;
        }
        return Intent.QUERY;
    }

    private Risk detectRisk(String text, Intent intent) {
        if (containsAny(text, "删除", "drop ", "force", "鉴权", "权限", "支付", "密码", "token", "生产", "数据库迁移")) {
            return Risk.HIGH;
        }
        if (intent == Intent.FEATURE || intent == Intent.BUG_FIX) {
            return Risk.MEDIUM;
        }
        return Risk.LOW;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public enum Intent { QUERY, CODE_REVIEW, BUG_FIX, FEATURE }

    public enum Risk { LOW, MEDIUM, HIGH }

    public record WorkflowPolicy(Intent intent, Risk risk, List<String> requiredGates) {
        public String asPrompt() {
            return "Intent: " + intent + "\nRisk: " + risk + "\nRequired gates: " + String.join(" -> ", requiredGates);
        }
    }
}
