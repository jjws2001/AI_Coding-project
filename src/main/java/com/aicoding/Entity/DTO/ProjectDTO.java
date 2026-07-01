package com.aicoding.Entity.DTO;

import com.aicoding.Entity.model.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDTO {
    private Long id;
    private String name;
    private String description;
    private String githubRepoUrl;
    private String githubRepoName;
    private Project.ProjectStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastBackupAt;
    private Long fileCount;
    private Long totalSize;

    public static ProjectDTO from(Project project) {
        return ProjectDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .githubRepoUrl(project.getGithubRepoUrl())
                .githubRepoName(project.getGithubRepoName())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .lastBackupAt(project.getLastBackupAt())
                .build();
    }
}
