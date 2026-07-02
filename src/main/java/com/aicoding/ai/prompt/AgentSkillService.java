package com.aicoding.ai.prompt;

import com.aicoding.Service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillService {

    private static final int MAX_SKILL_CHARS = 12_000;
    private final ProjectService projectService;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public String catalog(Long projectId) {
        return loadSkills(projectId).stream()
                .map(skill -> "- " + skill.name() + ": " + skill.description())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- No skills registered");
    }

    public String matchingSkillContent(Long projectId, String request) {
        String normalized = request == null ? "" : request.toLowerCase(Locale.ROOT);
        return loadSkills(projectId).stream()
                .filter(skill -> skill.triggers().stream().anyMatch(normalized::contains))
                .map(skill -> "## Skill: " + skill.name() + "\n" + skill.body())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private List<SkillDefinition> loadSkills(Long projectId) {
        LinkedHashMap<String, SkillDefinition> skills = new LinkedHashMap<>();
        loadBuiltInSkills().forEach(skill -> skills.put(skill.name(), skill));
        loadProjectSkills(projectId).forEach(skill -> skills.put(skill.name(), skill));
        return List.copyOf(skills.values());
    }

    private List<SkillDefinition> loadBuiltInSkills() {
        try {
            List<SkillDefinition> skills = new ArrayList<>();
            for (Resource resource : resolver.getResources("classpath*:agent/skills/*.md")) {
                try (var input = resource.getInputStream()) {
                    skills.add(parse(StreamUtils.copyToString(input, StandardCharsets.UTF_8), resource.getFilename()));
                }
            }
            return skills;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load built-in Agent skills", e);
        }
    }

    private List<SkillDefinition> loadProjectSkills(Long projectId) {
        Path skillDirectory;
        try {
            skillDirectory = projectService.getProjectRootPath(projectId).resolve(".aicoding/skills").normalize();
        } catch (RuntimeException e) {
            return List.of();
        }
        if (!Files.isDirectory(skillDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(skillDirectory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted()
                    .map(this::readProjectSkill)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to load project skills from {}", skillDirectory, e);
            return List.of();
        }
    }

    private SkillDefinition readProjectSkill(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return parse(limit(content), path.getFileName().toString());
        } catch (IOException | RuntimeException e) {
            log.warn("Ignoring invalid skill file {}", path, e);
            return null;
        }
    }

    private SkillDefinition parse(String markdown, String fallbackName) {
        String normalized = markdown.replace("\r\n", "\n");
        String name = fallbackName == null ? "unnamed" : fallbackName.replaceFirst("\\.md$", "");
        String description = "Project-specific operating procedure";
        List<String> triggers = new ArrayList<>();
        String body = normalized;

        if (normalized.startsWith("---\n")) {
            int end = normalized.indexOf("\n---\n", 4);
            if (end > 0) {
                String metadata = normalized.substring(4, end);
                body = normalized.substring(end + 5).strip();
                for (String line : metadata.split("\n")) {
                    String[] pair = line.split(":", 2);
                    if (pair.length != 2) continue;
                    String key = pair[0].strip();
                    String value = pair[1].strip();
                    if ("name".equals(key)) name = value;
                    if ("description".equals(key)) description = value;
                    if ("triggers".equals(key)) {
                        triggers = Arrays.stream(value.split(","))
                                .map(String::strip)
                                .map(term -> term.toLowerCase(Locale.ROOT))
                                .filter(term -> !term.isBlank())
                                .toList();
                    }
                }
            }
        }
        if (triggers.isEmpty()) {
            triggers = List.of(name.toLowerCase(Locale.ROOT));
        }
        return new SkillDefinition(name, description, triggers, limit(body));
    }

    private String limit(String content) {
        return content.length() <= MAX_SKILL_CHARS
                ? content
                : content.substring(0, MAX_SKILL_CHARS) + "\n[skill truncated]";
    }

    private record SkillDefinition(String name, String description, List<String> triggers, String body) {}
}
