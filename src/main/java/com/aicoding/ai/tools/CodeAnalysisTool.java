package com.aicoding.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CodeAnalysisTool {

    @Tool("Analyze code complexity and provide metrics")
    public String analyzeComplexity(String code) {
        log.info("Tool: Analyzing code complexity");
        // 统计代码行数
        int lines = code.split("\n").length;
        // 用正则统计方法数量：匹配 public/private/protected xxx xxx(...)
        int methods = countOccurrences(code, "(public|private|protected)\\s+\\w+\\s+\\w+\\s*\\(");
        // 统计类/接口数量：匹配 class/interface/enum xxx
        int classes = countOccurrences(code, "(class|interface|enum)\\s+\\w+");
        // 统计循环数量：匹配 for/while/do (...)
        int loops = countOccurrences(code, "(for|while|do)\\s*\\(");
        // 统计条件语句：匹配 if/else/switch (...)
        int conditions = countOccurrences(code, "(if|else|switch)\\s*\\(");
        // 返回分析报告
        return String.format("""
            Code Analysis Results:
            - Lines of Code: %d
            - Classes/Interfaces: %d
            - Methods: %d
            - Loops: %d
            - Conditional Statements: %d
            - Estimated Complexity: %s
            """, lines, classes, methods, loops, conditions,
                estimateComplexity(methods, loops, conditions));
    }

    @Tool("Extract all function/method names from code")
    public String extractMethods(String code) {
        log.info("Tool: Extracting methods from code");

        Pattern pattern = Pattern.compile(
                "(public|private|protected|static)?\\s*\\w+\\s+(\\w+)\\s*\\([^)]*\\)");
        Matcher matcher = pattern.matcher(code);

        StringBuilder methods = new StringBuilder("Methods found:\n");
        while (matcher.find()) {
            methods.append("- ").append(matcher.group(2)).append("\n");
        }

        return methods.toString();
    }

    @Tool("Find potential code issues and anti-patterns")
    public String findIssues(String code) {
        log.info("Tool: Finding code issues");

        StringBuilder issues = new StringBuilder("Potential Issues:\n");

        // 检查过长的方法
        String[] methods = code.split("\\{");
        for (String method : methods) {
            if (method.split("\n").length > 50) {
                issues.append("- Found method longer than 50 lines (consider refactoring)\n");
            }
        }

        // 检查嵌套过深
        int maxNesting = findMaxNesting(code);
        if (maxNesting > 4) {
            issues.append("- Deep nesting detected (max depth: ")
                    .append(maxNesting).append(")\n");
        }

        // 检查魔法数字
        if (code.matches(".*\\b\\d{2,}\\b.*")) {
            issues.append("- Magic numbers detected (consider using constants)\n");
        }

        return issues.length() > 19 ? issues.toString() : "No obvious issues found";
    }

    private int countOccurrences(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private String estimateComplexity(int methods, int loops, int conditions) {
        int score = methods + loops * 2 + conditions;
        if (score < 10) return "LOW";
        if (score < 30) return "MEDIUM";
        return "HIGH";
    }

    private int findMaxNesting(String code) {
        int current = 0;
        int max = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') current++;
            if (c == '}') current--;
            max = Math.max(max, current);
        }
        return max;
    }
}