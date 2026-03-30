package com.aicoding.Controller;

import com.aicoding.Entity.DTO.ProjectDTO;
import com.aicoding.Entity.model.Project;
import com.aicoding.Entity.model.User;
import com.aicoding.Service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getUserProjects(
            @AuthenticationPrincipal OAuth2User principal) {
        Long userId = getCurrentUserId(principal);
        return ResponseEntity.ok(projectService.getUserProjects(userId));
    }

    @PostMapping("/upload")
    public ResponseEntity<Project> uploadProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "githubRepo", required = false) String githubRepo,
            @AuthenticationPrincipal OAuth2User principal) {

        Long userId = getCurrentUserId(principal);
        String githubToken = getGitHubToken(principal);

        Project project = projectService.createProjectFromUpload(
                userId, name, githubRepo, file, githubToken
        );

        return ResponseEntity.ok(project);
    }

    @PostMapping("/import/github")
    public ResponseEntity<Project> importFromGitHub(
            @RequestParam("githubRepo") String githubRepo,
            @AuthenticationPrincipal OAuth2User principal) {

        Long userId = getCurrentUserId(principal);
        String githubToken = getGitHubToken(principal);

        Project project = projectService.createProjectFromGitHub(
                userId, githubRepo, githubToken
        );

        return ResponseEntity.ok(project);
    }

    @PostMapping("/{projectId}/sync")
    public ResponseEntity<Void> syncWithGitHub(
            @PathVariable Long projectId,
            @AuthenticationPrincipal OAuth2User principal) {

        String githubToken = getGitHubToken(principal);
        projectService.syncWithGitHub(projectId, githubToken);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{projectId}/backup")
    public ResponseEntity<String> backupProject(@PathVariable Long projectId) {
        String backupPath = projectService.backupProject(projectId);
        return ResponseEntity.ok(backupPath);
    }

    @GetMapping("/{projectId}/files")
    public ResponseEntity<?> getProjectFiles(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProjectFileTree(projectId));
    }

    @GetMapping("/{projectId}/files/{*filePath}")
    public ResponseEntity<String> getFileContent(
            @PathVariable Long projectId,
            @PathVariable String filePath) {
        String content = projectService.getFileContent(projectId, filePath);
        return ResponseEntity.ok(content);
    }

    private Long getCurrentUserId(OAuth2User principal) {
        // 从OAuth2User提取GitHub ID并查找对应的User
        return 1L; // 示例
    }

    private String getGitHubToken(OAuth2User principal) {
        // 从session或数据库获取GitHub token
        return "token"; // 示例
    }
}
