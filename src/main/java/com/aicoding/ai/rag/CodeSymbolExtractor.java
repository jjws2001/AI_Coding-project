package com.aicoding.ai.rag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CodeSymbolExtractor {

    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(?:class|interface|enum|record|object|type)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern NAMED_FUNCTION = Pattern.compile(
            "\\b(?:fun|function)\\s+(?:<[^>]+>\\s*)?([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern PYTHON_FUNCTION = Pattern.compile(
            "(?m)^\\s*(?:async\\s+)?def\\s+([A-Za-z_][\\w]*)\\s*\\(");
    private static final Pattern GO_FUNCTION = Pattern.compile(
            "\\bfunc\\s+(?:\\([^)]*\\)\\s*)?([A-Za-z_][\\w]*)\\s*\\(");
    private static final Pattern ARROW_FUNCTION = Pattern.compile(
            "\\b(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_$][\\w$]*)\\s*=>");
    private static final Pattern JVM_METHOD = Pattern.compile(
            "(?m)^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp|override|open|internal|external|suspend|inline|operator)\\s+)*"
                    + "(?:<[^>\\n]+>\\s*)?(?:[\\w$?.<>\\[\\],]+\\s+)+([A-Za-z_$][\\w$]*)\\s*\\(");

    private CodeSymbolExtractor() {
    }

    static Set<String> extract(String fileName, String text) {
        String extension = extension(fileName);
        Set<String> symbols = new LinkedHashSet<>();
        collect(TYPE_DECLARATION, text, symbols);
        switch (extension) {
            case "java", "kt" -> {
                collect(NAMED_FUNCTION, text, symbols);
                collect(JVM_METHOD, text, symbols);
            }
            case "py" -> collect(PYTHON_FUNCTION, text, symbols);
            case "go" -> collect(GO_FUNCTION, text, symbols);
            case "js", "jsx", "ts", "tsx" -> {
                collect(NAMED_FUNCTION, text, symbols);
                collect(ARROW_FUNCTION, text, symbols);
            }
            default -> {
            }
        }
        return Set.copyOf(symbols);
    }

    private static void collect(Pattern pattern, String text, Set<String> symbols) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            symbols.add(matcher.group(1));
        }
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
