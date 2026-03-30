package com.aicoding.Entity.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    private String description;

//    @Column(nullable = false)
    private String localPath;  // 本地工作目录

    private String githubRepoUrl;  // GitHub仓库URL
    private String githubRepoName; // 仓库名

    private String minioBackupPath;  // MinIO备份路径

    @Enumerated(EnumType.STRING)
    private ProjectStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastBackupAt;

    public enum ProjectStatus {
        INITIALIZING, ACTIVE, ARCHIVED, ERROR
    }
}
