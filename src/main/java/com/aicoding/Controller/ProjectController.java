package com.aicoding.Controller;

import com.aicoding.Entity.DTO.ProjectDTO;
import com.aicoding.Entity.model.CustomOAuth2User;
import com.aicoding.Entity.model.Project;
import com.aicoding.Exception.ProjectException;
import com.aicoding.Service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getUserProjects(
            @AuthenticationPrincipal CustomOAuth2User principal) {
        return ResponseEntity.ok(projectService.getUserProjects(principal.getId()));
    }

    @PostMapping("/upload")
    public ResponseEntity<ProjectDTO> uploadProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "githubRepo", required = false) String githubRepo,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        Project project = projectService.createProjectFromUpload(
                principal.getId(), name, githubRepo, file, githubToken(principal)
        );
        return ResponseEntity.ok(ProjectDTO.from(project));
    }

    @PostMapping("/import/github")
    public ResponseEntity<ProjectDTO> importFromGitHub(
            @RequestParam("githubRepo") String githubRepo,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        Project project = projectService.createProjectFromGitHub(
                principal.getId(), githubRepo, githubToken(principal)
        );
        return ResponseEntity.ok(ProjectDTO.from(project));
    }

    @PostMapping("/{projectId}/sync")
    public ResponseEntity<Void> syncWithGitHub(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(projectId, principal);
        projectService.syncWithGitHub(projectId, githubToken(principal));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{projectId}/backup")
    public ResponseEntity<String> backupProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(projectId, principal);
        return ResponseEntity.ok(projectService.backupProject(projectId));
    }

    @GetMapping("/{projectId}/files")
    public ResponseEntity<?> getProjectFiles(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(projectId, principal);
        return ResponseEntity.ok(projectService.getProjectFileTree(projectId));
    }

    @GetMapping("/{projectId}/files/{*filePath}")
    public ResponseEntity<String> getFileContent(
            @PathVariable Long projectId,
            @PathVariable String filePath,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(projectId, principal);
        return ResponseEntity.ok(projectService.getFileContent(projectId, filePath));
    }

    @GetMapping("/{projectId}/file-content")
    public ResponseEntity<String> getFileContentByQuery(
            @PathVariable Long projectId,
            @RequestParam("path") String filePath,
            @AuthenticationPrincipal CustomOAuth2User principal) {
        requireOwnership(projectId, principal);
        return ResponseEntity.ok(projectService.getFileContent(projectId, filePath));
    }

    private void requireOwnership(Long projectId, CustomOAuth2User principal) {
        projectService.getProjectByIdAndUserId(projectId, principal.getId());
    }

    private String githubToken(CustomOAuth2User principal) {
        String token = principal.getUser().getGithubAccessToken();
        if (token == null || token.isBlank()) {
            throw new ProjectException("GitHub access token is unavailable; sign in again");
        }
        return token;
    }
}
